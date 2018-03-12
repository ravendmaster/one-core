package com.ravendmaster.onecore.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.util.JsonReader
import com.jayway.jsonpath.JsonPath

import com.ravendmaster.onecore.Utilities
import com.ravendmaster.onecore.customview.Graph
import com.ravendmaster.onecore.database.DbHelper
import com.ravendmaster.onecore.database.HistoryCollector
import com.ravendmaster.onecore.Log
import com.ravendmaster.onecore.activity.MainActivity
import com.ravendmaster.onecore.R
import com.squareup.duktape.Duktape
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*

import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream


class MQTTService() : Service() {

    init {
        Log.d(javaClass.name, "constructor MQTTService()")
    }

    private var push_topic: String? = null
    private var currentDataVersion = 0
    var mqttAndroidClient : MqttAndroidClient? = null
    var dashboards: ArrayList<Dashboard>? = null

    //private var mqttBroker = org.eclipse.moquette.server.Server()

    val freeDashboardId: Int
        get() {
            val result = dashboards!!
                    .map { it.id }
                    .max()
                    ?: -1
            return result + 1
        }

    private var lastReceivedMessagesByTopic: HashMap<String, String>? = null

    var currentMQTTValues: HashMap<String, String> = HashMap()

    var activeTabIndex = 0
    var screenActiveTabIndex = 0

    private var duktape: Duktape? = null

    private var contextWidgetData: WidgetData? = null

    private var imqtt: IMQTT = object : IMQTT {
        override fun read(topic: String): String {
            return getMQTTCurrentValue(topic)
        }

        override fun publish(topic: String, text: String) {
            publishMQTTMessage(topic, textMessage(text, false))
        }

        override fun publishr(topic: String, text: String) {
            publishMQTTMessage(topic, textMessage(text, true))
        }
    }

    fun textMessage(text: String, retained: Boolean): MqttMessage {
        val message = MqttMessage(text.toByteArray())
        message.isRetained = retained
        return message
    }


    private var notifier: INotifier = object : INotifier {
        override fun push(message: String) {
            publishMQTTMessage(getServerPushNotificationTopicForTextMessage(contextWidgetData!!.uid.toString()), textMessage(message, true))
        }

        override fun stop() {
            publishMQTTMessage(getServerPushNotificationTopicForTextMessage(contextWidgetData!!.uid.toString()), textMessage("", true))
        }
    }

    private var jsonPath: IJSONPath = object : IJSONPath {
        override fun read(text: String, path: String): String {
            val ctx = JsonPath.parse(text)
            return ctx.read<Object>(path).toString()
        }
    }

    private var valueData: IValue = object : IValue {
        override fun get(): String {
            return tempValue
        }
    }

    val publishConfigTopicRootPath: String
        get() {
            val rootPushTopic = AppSettings.instance.push_notifications_subscribe_topic
            return rootPushTopic.replace("#", "") + "\$config"
        }

    //корень топиков от сервера приложения
    val serverPushNotificationTopicRootPath: String
        get() {
            val rootPushTopic = AppSettings.instance.push_notifications_subscribe_topic
            return rootPushTopic.replace("#", "") + "\$server"
        }

    val topicsForHistoryCollect: HashMap<String, String>
        get() {
            val graph_topics = HashMap<String, String>()
            for (dashboard in dashboards!!) {
                for (widgetData in dashboard.widgetsList) {
                    if (widgetData.type != WidgetData.WidgetTypes.GRAPH) continue
                    for (i in 0..3) {
                        val topic = widgetData.getSubTopic(i)
                        if (!topic.isEmpty() && widgetData.mode >= Graph.PERIOD_TYPE_1_HOUR) {
                            graph_topics.put(topic, topic)
                        }
                    }
                }
            }
            return graph_topics
        }

    private val topicsForLiveCollect: HashMap<String, String>
        get() {
            val graphTopics = HashMap<String, String>()
            for (dashboard in dashboards!!) {
                for (widgetData in dashboard.widgetsList) {
                    if (widgetData.type != WidgetData.WidgetTypes.GRAPH) continue
                    for (i in 0..3) {
                        val topic = widgetData.getSubTopic(i)
                        if (!topic.isEmpty() && widgetData.mode == Graph.LIVE) {
                            graphTopics.put(topic, topic)
                        }
                    }
                }
            }
            return graphTopics
        }

    var topicsForHistory: HashMap<String, String>? = null
    private var topicsForLive: HashMap<String, String>? = null
    var SERVER_DATAPACK_NAME = ""

    var historyCollector: HistoryCollector? = null

    val isConnected: Boolean
        get() = mqttAndroidClient!!.isConnected

    private var mPayloadChanged: Handler? = null

    var currentSessionTopicList = ArrayList<String>()

    fun getDashboardByID(id: Int): Dashboard? {
        dashboards!!
                .filter { it.id == id }
                .forEach { return it }
        Exception("can't find dashboard by ID")
        return null
    }

    fun getMQTTCurrentValue(topic: String): String {
        //if (currentMQTTValues == null) return ""

        val value = currentMQTTValues[topic]

        return value ?: ""
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String{
        val channelId = "my_service"
        val channelName = "My Background Service"
        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createPushNotificationChannel(): String{
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "PushNotification"
        val channelName = getString(R.string.ChanelNamePushNotification)
        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        chan.enableLights(true)
        chan.lightColor=Color.RED
        service.createNotificationChannel(chan)
        return channelId
    }

    var tempValue:String=""

    fun evalJS(contextWidgetData: WidgetData, value: String?, code: String): String? {
        this.contextWidgetData = contextWidgetData
        var result = value
        try {
            result = duktape!!.evaluate("var value==ValueData.get(); $code; String(value);")
            processReceiveSimplyTopicPayloadData("%onJSErrors", "no errors");
        } catch (e: Exception) {
            Log.d("script", "exec: " + e)
            processReceiveSimplyTopicPayloadData("%onJSErrors", e.toString() );
        }

        return result
    }

    internal interface IMQTT {
        fun read(topic: String): String

        fun publish(topic: String, payload: String)

        fun publishr(topic: String, payload: String)
    }

    internal interface INotifier {
        fun push(message: String)

        fun stop()
    }

    internal interface IJSONPath {
        fun read(text: String, path: String): String
    }

    internal interface IValue {
        fun get(): String
    }

    fun getServerPushNotificationTopicForTextMessage(id: String): String { //для текстовых сообщений
        return serverPushNotificationTopicRootPath + "/message" + Integer.toHexString(id.hashCode())
    }

    fun createDashboardsBySettings(forceReload : Boolean = false) {

        if(!forceReload && dashboards!=null)return

        Log.d(javaClass.name, "createDashboardsBySettings()")

        dashboards = ArrayList()

        if (AppSettings.instance.settingsVersion == 0) {
            /*
            //старый способ
            val tabs = AppSettings.instance.tabs
            for (i in 0..3) {
                if (tabs!!.items.size <= i) break
                val tabData = tabs.items[i]
                if (tabData == null || tabData.name == "") {
                    continue
                }
                val tempDashboard = Dashboard(i)
                tempDashboard.updateFromSettings()
                dashboardsConfiguration!!.add(tempDashboard)
            }
            */

        } else {
            for (tabData in AppSettings.instance.tabs!!.items) {
                val tempDashboard = Dashboard(tabData.id)
                tempDashboard.updateFromSettings()
                dashboards!!.add(tempDashboard)

            }

        }

    }

    @Throws(IOException::class)
    private fun copyFile(input: InputStream, out: OutputStream) {
        val buffer = ByteArray(1024)
        var read: Int
        while (true){
        read = input.read(buffer)
            if(read== -1)break
            out.write(buffer, 0, read)
        }
    }


    private fun prepareHistoryGraphicData(sourceTopic: String, period_types: IntArray): String {
        //добыча id топика
        val topicId = historyCollector!!.getTopicIDByName(sourceTopic)

        //аггрегация
        val resultJson = JSONObject()
        val graphics = JSONArray()

        for (period_type in period_types) {

            val aggregationPeriod = Graph.aggregationPeriod[period_type].toLong()
            val periodsCount = Graph.getPeriodCount(period_type)

            val period = aggregationPeriod * periodsCount
            val mass = arrayOfNulls<Float>(periodsCount + 1)

            val now = Date()
            var timeNow = now.time

            val c = GregorianCalendar()
            when (period_type) {
                Graph.PERIOD_TYPE_4_HOUR -> {
                    c.add(Calendar.MINUTE, 30)

                    c.set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY), if (c.get(Calendar.MINUTE) < 30) 0 else 30, 0)
                    timeNow += c.time.time - timeNow
                }
                Graph.PERIOD_TYPE_1_DAY -> {
                    c.add(Calendar.HOUR_OF_DAY, 1)
                    c.set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY), 0, 0)
                    timeNow += c.time.time - timeNow
                }
                Graph.PERIOD_TYPE_1_WEEK, Graph.PERIOD_TYPE_1_MOUNT -> {
                    c.add(Calendar.DAY_OF_YEAR, 1)
                    c.set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                    timeNow += c.time.time - timeNow
                }
            }

            val nowRaw = (timeNow / aggregationPeriod) * aggregationPeriod
            val timeLine = nowRaw - period

            //данные за период
            var selectQuery = "SELECT  MAX(timestamp/?), COUNT(timestamp), ROUND(AVG(value),2) FROM HISTORY WHERE detail_level=0 AND topic_id=? AND timestamp>=? GROUP BY timestamp/? ORDER BY timestamp DESC"
            var cursor = MQTTService.db!!.rawQuery(selectQuery, arrayOf(aggregationPeriod.toString(), topicId.toString(), timeLine.toString(), aggregationPeriod.toString()))
            if (cursor.moveToFirst()) {
                var i = 1
                do {
                    val res = cursor.getFloat(2)
                    val index = (nowRaw / aggregationPeriod - cursor.getLong(0)).toInt()
                    if (index in 0..periodsCount) {
                        mass[index] = res
                    }
                    i++
                } while (cursor.moveToNext())
            }
            cursor.close()


            //актуальное значение
            selectQuery = "SELECT ROUND(value,2) FROM HISTORY WHERE detail_level=0 AND topic_id=? ORDER BY timestamp DESC LIMIT 1"
            cursor = MQTTService.db!!.rawQuery(selectQuery, arrayOf(topicId.toString()))
            var actualValue: Float? = null
            if (cursor.moveToFirst()) {
                actualValue = cursor.getFloat(0)
            }
            cursor.close()

            if (period_type <= Graph.PERIOD_TYPE_1_HOUR && actualValue != null) { //нужно только для живого и часового графиков, более крупным - нет

                (0 until periodsCount)
                        .takeWhile { mass[it] == null }
                        .forEach { mass[it] = actualValue }
                //заполняем пропуски
                var lastVal: Float? = null
                for (i in periodsCount - 1 downTo 0) {
                    if (mass[i] == null) {
                        mass[i] = lastVal
                    } else {
                        lastVal = mass[i]
                    }
                }


            }


            val dots = JSONArray()
            for (i in 0 until periodsCount) {
                dots.put(if (mass[i] == null) "" else mass[i])
            }

            val graphLineJSON = JSONObject()
            try {
                graphLineJSON.put("period_type", period_type)
                graphLineJSON.put("actual_timestamp", timeNow)
                graphLineJSON.put("aggregation_period", aggregationPeriod)
                graphLineJSON.put("dots", dots)
            } catch (e: JSONException) {
                e.printStackTrace()
            }

            graphics.put(graphLineJSON)
        }

        try {
            resultJson.put("type", "graph_history")
            resultJson.put("graphics", graphics)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return resultJson.toString()

    }

    class HistoryDataCollectorTask(mqtt:MQTTService) : TimerTask() {

        private var mMqtt : MQTTService;

        init {
            mMqtt=mqtt;
        }


        override fun run() {

            val appSettings = AppSettings.instance
            if(appSettings==null)return;

            if (appSettings.server_mode && MQTTService.db != null && mMqtt.dashboards != null) {

                mMqtt.topicsForHistory = mMqtt.topicsForHistoryCollect

                //аггрегация данных для графиков
                val universalPackJson = JSONObject()
                val topicsData = JSONArray()
                try {
                    val strEnum = Collections.enumeration(mMqtt.topicsForHistory!!.keys)
                    while (strEnum.hasMoreElements()) {
                        val topicForHistoryData = strEnum.nextElement()//widgetData.getSubTopic(0).substring(0, widgetData.getSubTopic(0).length() - 4);
                        val historyData = mMqtt.prepareHistoryGraphicData(topicForHistoryData, intArrayOf(Graph.PERIOD_TYPE_1_HOUR, Graph.PERIOD_TYPE_4_HOUR, Graph.PERIOD_TYPE_1_DAY, Graph.PERIOD_TYPE_1_WEEK, Graph.PERIOD_TYPE_1_MOUNT))
                        val oneTopicData = JSONObject()
                        oneTopicData.put("topic", topicForHistoryData + Graph.HISTORY_TOPIC_SUFFIX)
                        oneTopicData.put("payload", historyData)
                        topicsData.put(oneTopicData)
                        Log.d("servermode", "source len:" + historyData.length)
                    }

                    universalPackJson.put("ver", 1)
                    universalPackJson.put("type", MQTTService.TOPICS_DATA)
                    universalPackJson.put("data", topicsData.toString())

                    val universalPackJsonResult = universalPackJson.toString()

                    //сжимаем
                    val bo = ByteArrayOutputStream()
                    val os = ZipOutputStream(BufferedOutputStream(bo))
                    try {
                        os.putNextEntry(ZipEntry("data"))
                        val buff = Utilities.stringToBytesUTFCustom(universalPackJsonResult)
                        os.flush()
                        os.write(buff)
                        os.close()
                        //os.closeEntry();
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                    val message=MqttMessage(bo.toByteArray())
                    message.isRetained=true
                    mMqtt.publishMQTTMessage(mMqtt.SERVER_DATAPACK_NAME, message)

                    Log.d("servermode", "universal data source len:" + universalPackJsonResult.length + " zipped len:" + bo.toByteArray().size)


                } catch (e: JSONException) {
                    e.printStackTrace()
                }

            }



        }

    }

    fun connectionSettingsChanged() {

        mqttAndroidClient!!.disconnect()

/*
        while(mqttAndroidClient!!.isConnected){
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

        }
 */
        mqttAndroidClient=null;


        connectToMQTT()


        //connectionInUnActualMode = true
    }



    fun subscribeForInteractiveMode(appSettings: AppSettings) {
        mqttAndroidClient!!.subscribe("#", 0)
    }

    private fun subscribeForBackgroundMode(appSettings: AppSettings) {
        mqttAndroidClient!!.unsubscribe("#")
        mqttAndroidClient!!.subscribe(appSettings.push_notifications_subscribe_topic, 0)
    }


    fun subscribeForState(newState: Int) {
        val appSettings = AppSettings.instance
        appSettings.readPrefsFromDisk()

        when (newState) {
            STATE_FULL_CONNECTED ->
                //3.0 callbackMQTTClient.subscribe(appSettings.subscribe_topic);
                subscribeForInteractiveMode(appSettings)
            STATE_HALF_CONNECTED -> if (appSettings.connection_in_background) {
                //3.0 callbackMQTTClient.subscribe(appSettings.push_notifications_subscribe_topic);
                subscribeForBackgroundMode(appSettings)
            }
        }
    }

    val subscriptionTopic="#"
    fun subscribeToTopic() {
        try {
            mqttAndroidClient!!.subscribe(subscriptionTopic, 0, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    ///addToHistory("Subscribed!")
                    Log.d("paho", "Subscribed!")
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    ///addToHistory("Failed to subscribe")
                    Log.d("paho", "Failed to subscribe")
                }
            })

        } catch (ex: MqttException) {
            System.err.println("Exception whilst subscribing")
            ex.printStackTrace()
        }

    }


    override fun onCreate() {
        super.onCreate()
        Log.d(javaClass.name, "onCreate()")

        connectToMQTT();

        //end paho

    }


    var needFullConnect=false

    private fun connectToMQTT(){
        Log.d(javaClass.name, "connectToMQTT()")

        val appSettings = AppSettings.instance
        appSettings.readPrefsFromDisk()


        val ANDROID_ID = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        mqttAndroidClient=MqttAndroidClient(applicationContext, appSettings.server, "OneCore_$ANDROID_ID"+System.currentTimeMillis())

        mqttAndroidClient!!.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String) {

                Log.d("paho", "connectComplete")
                //subscribeForState(STATE_FULL_CONNECTED)


                if (reconnect) {
                    ///addToHistory("Reconnected to : $serverURI")
                    // Because Clean Session is true, we need to re-subscribe



                    //subscribeToTopic()
                    //subscribeForState(STATE_HALF_CONNECTED)
                    //subscribeForState(STATE_FULL_CONNECTED)
                } else {
                    ///addToHistory("Connected to: $serverURI")
                    //subscribeToTopic()
                }

                interactiveMode(needFullConnect)
                //subscribeForState(STATE_HALF_CONNECTED)

            }

            override fun connectionLost(cause: Throwable?) {
                Log.d("paho", "connection lost")
                ///addToHistory("The Connection was lost.")
            }

            @Throws(Exception::class)
            override fun messageArrived(topic: String, message: MqttMessage) {
                //Log.d("paho", "messageArrived $topic $message")
                ///addToHistory("Incoming message: " + String(message.payload))

                var urbanTopic = topic.toString()
                if (urbanTopic[urbanTopic.length - 1] == '$') {
                    urbanTopic = urbanTopic.substring(0, urbanTopic.length - 1)
                }
                //Log.d(javaClass.name, "onPublish "+urbanTopic+" payload:"+message);
                onReceiveMQTTMessage(urbanTopic, message)

            }

            override fun deliveryComplete(token: IMqttDeliveryToken) {

            }
        })


        val mqttConnectOptions = MqttConnectOptions()
        mqttConnectOptions.isAutomaticReconnect = true
        mqttConnectOptions.isCleanSession = true


        try {
            mqttAndroidClient!!.connect(mqttConnectOptions, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    val disconnectedBufferOptions = DisconnectedBufferOptions()
                    disconnectedBufferOptions.isBufferEnabled = true
                    disconnectedBufferOptions.bufferSize = 100
                    disconnectedBufferOptions.isPersistBuffer = false
                    disconnectedBufferOptions.isDeleteOldestMessages = false
                    mqttAndroidClient!!.setBufferOpts(disconnectedBufferOptions)
                    subscribeForState(STATE_HALF_CONNECTED)
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    Log.d("paho", "Failed to connect to server")
                }
            })


        } catch (ex: MqttException) {
            ex.printStackTrace()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d(javaClass.name, "onDestroy()")

        val broadcastIntent = Intent("com.ravendmaster.onecore.RestartMQTTService")
        sendBroadcast(broadcastIntent)


        stoptimertask()

    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(javaClass.name, "onStartCommand()")

        if(instance!=null)return Service.START_STICKY

        instance = this

        AppSettings.instance.readPrefsFromDisk()
        createDashboardsBySettings(true)

        duktape = Duktape.create()
        duktape!!.bind("MQTT", IMQTT::class.java, imqtt)
        duktape!!.bind("Notifier", INotifier::class.java, notifier)
        duktape!!.bind("JSONPath", IJSONPath::class.java, jsonPath)
        duktape!!.bind("ValueData", IValue::class.java, valueData)

        Log.d(javaClass.name, "duktape start")


        lastReceivedMessagesByTopic = HashMap()

        currentMQTTValues = HashMap()


        var mTimer = Timer()
        var mMyTimerTask = HistoryDataCollectorTask(this)
        mTimer.schedule(mMyTimerTask, 100, 60000)


        Thread(Runnable {
            //живые данные
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            val appSettings = AppSettings.instance


            while (true) {

                if (( appSettings.server_mode) && db != null && dashboards != null && historyCollector!=null) {

                    topicsForLive = topicsForLiveCollect

                    val strEnum = Collections.enumeration(topicsForLive!!.keys)
                    while (strEnum.hasMoreElements()) {
                        val topicForHistoryData = strEnum.nextElement()//widgetData.getSubTopic(0).substring(0, widgetData.getSubTopic(0).length() - 4);
                        val historyData = prepareHistoryGraphicData(topicForHistoryData, intArrayOf(Graph.LIVE))
                        processReceiveSimplyTopicPayloadData(topicForHistoryData + Graph.LIVE_TOPIC_SUFFIX, historyData)
                    }
                }

                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

            }
        }).start()

        //$timer_1m
        Thread(Runnable {
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            while (true) {
                val appSettings = AppSettings.instance;
                if (appSettings.server_mode) {
                    val cal = Calendar.getInstance();
                    val dateFormat = SimpleDateFormat("HH:mm")
                    processReceiveSimplyTopicPayloadData("onTimer1m()", dateFormat.format(cal.getTime()));
                }
                try {
                    Thread.sleep(60000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }).start()


        //$timer_1s
        Thread(Runnable {
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            while (true) {
                val appSettings = AppSettings.instance;
                if (appSettings.server_mode) {
                    val cal = Calendar.getInstance();
                    val dateFormat = SimpleDateFormat("HH:mm:ss")
                    processReceiveSimplyTopicPayloadData("onTimer1s()", dateFormat.format(cal.getTime()));
                }
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }).start()

        val appSettings = AppSettings.instance
        appSettings.readPrefsFromDisk()


        push_topic = appSettings.push_notifications_subscribe_topic

        SERVER_DATAPACK_NAME = appSettings.server_topic

        if (mDbHelper == null) {

            val dbPath=Utilities.getAppDir().absolutePath+File.separator+"onecore.db"

            mDbHelper = DbHelper(applicationContext, dbPath)
            db = mDbHelper!!.writableDatabase
            historyCollector = HistoryCollector(db)
        }
        if(historyCollector!=null) {
            historyCollector!!.needCollectData = appSettings.server_mode
        }


        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (wl == null) {
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, javaClass.name)
        }



        createDashboardsBySettings()

        activeTabIndex = appSettings.tabs!!.getDashboardIdByTabIndex(screenActiveTabIndex)


        startTimer()

        return Service.START_STICKY
    }

    internal fun showNotifyStatus(text1: String, cancel: Boolean?) {
        val foreground_intent = Intent(this, MainActivity::class.java)

        foreground_intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        foreground_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)


        var channelId =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    createNotificationChannel()
                }else{ "" }

        val pendingIntent = PendingIntent.getActivity(this, 0, foreground_intent, 0)
        val builder = NotificationCompat.Builder(this, channelId)
                //.setContentTitle("Online")
                //.setContentTitle("OneCore")
                .setContentText("Online")
                .setSmallIcon(R.drawable.ic_playblack)
                .setContentIntent(pendingIntent)
                .setOngoing(true).setSubText(text1)
                .setAutoCancel(true)

        if (cancel!!) {
            stopForeground(true)
        } else {
            startForeground(1, builder.build())
        }
    }


    internal fun showPushNotification(topic: String, message: String) {

        val intent = Intent(this, MainActivity::class.java)

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)

        //val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)


        var chanelId =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    createPushNotificationChannel()
                }else{ "" }

        val intent1 = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(getApplicationContext(), 123, intent1, PendingIntent.FLAG_UPDATE_CURRENT);

        var builder=NotificationCompat.Builder(applicationContext, chanelId)
                .setContentTitle(message)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setBadgeIconType(R.drawable.ic_notification)
                .setChannelId(chanelId)
                .setNumber(1)
                .setColor(Color.BLACK)
                .setWhen(System.currentTimeMillis())
/*
        val builder = Notification.Builder(this)
                .setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
                .setLights(Color.RED, 100, 100)

                .setContentTitle(message)
                //.setContentTitle("Linear MQTT Dashboard")
                //.setContentText("You have a new notification")

                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                //.setSubText(message)//"This is subtext...");   //API level 16
                .setAutoCancel(true)
*/
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(getStringHash(topic), builder.build())// myNotication);

    }

    internal fun getStringHash(text: String): Int {
        var hash = 7
        for (i in 0 until text.length) {
            hash = hash * 31 + text[i].toInt()
        }
        return hash
    }

    fun publishMQTTMessage(topic: String, message: MqttMessage) {
        if (topic == "") return
        try {
            mqttAndroidClient!!.publish(topic, message)

            //if (!mqttAndroidClient!!.isConnected()) {
            //}
        } catch (e: MqttException) {
            System.err.println("Error Publishing: " + e.message)
            e.printStackTrace()
        }


    }

    fun setPayLoadChangeHandler(payLoadChanged: Handler) {
        Log.d(javaClass.name, "setPayLoadChangeHandler()")
        mPayloadChanged = payLoadChanged
    }

    internal fun notifyDataInTopicChanged(topic: String?, payload: String?) {
        Log.d(javaClass.name,"$this notifyDataInTopicChanged():"+topic+" - "+payload)
        if (mPayloadChanged != null) {
            if (payload != currentMQTTValues[topic]) {
                val msg = Message()
                msg.obj = topic
                mPayloadChanged!!.sendMessage(msg)
            }
        }
    }

    fun onReceiveMQTTMessage(topic: String, payload: MqttMessage) {

        //Log.d(javaClass.name, "onReceiveMQTTMessage() topic:" + topic+" payload:"+String(payload.toByteArray(), Charset.forName("UTF-8")))

        if( topic == publishConfigTopicRootPath){

            val is_ = ByteArrayInputStream(payload.payload)
            val inputStream = ZipInputStream(BufferedInputStream(is_!!))

            //ZipEntry entry;
            while (inputStream.nextEntry != null) {
                val os = ByteArrayOutputStream()

                val buff = ByteArray(1024)
                while (true){
                    val count = inputStream.read(buff, 0, 1024)
                    if(count == -1)break
                    os.write(buff, 0, count)
                }
                os.flush()
                os.close()

                val result = Utilities.bytesToStringUTFCustom(os.toByteArray(), os.toByteArray().size)


                val settings = AppSettings.instance
                settings.setSettingsFromString(result)

                settings.saveTabsSettingsToPrefs()

                createDashboardsBySettings(true)

                MainActivity.getPresenter().saveAllDashboards()
                MainActivity.getPresenter().onTabPressed(0)
                MainActivity.getPresenter().view.refreshTabState()

            }

        }else if (topic == SERVER_DATAPACK_NAME) {
            processUniversalPack(payload.payload)
        } else {

            if (currentSessionTopicList.indexOf(topic) == -1) {
                currentSessionTopicList.add(topic)
                Log.d("currentSessionTopicList", "add:" + topic)
            }

            var payloadAsString = ""
            try {
                payloadAsString = payload.toString()
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
            }

            if (historyCollector != null && topic != SERVER_DATAPACK_NAME) {
                if (topicsForHistory != null && topicsForHistory!![topic] != null || topicsForLive != null && topicsForLive!![topic] != null) {
                    historyCollector!!.collect(topic, payloadAsString)
                    //Log.d("collect", ""+payloadAsString);
                }
            }
            processReceiveSimplyTopicPayloadData(topic, payloadAsString)
        }
    }

    private fun processUniversalPack(payload: ByteArray) {

        var payloadAsString: String? = null
        try {
            //payloadAsString = new String(payload.toByteArray(), "UTF-8");
            //разжимае
            val is_ = ByteArrayInputStream(payload)
            val istream = ZipInputStream(BufferedInputStream(is_))

            //int version=is.read();
            //var entry: ZipEntry?
            while (true){
                if(istream.nextEntry==null)break
                //entry = istream.nextEntry

                val os = ByteArrayOutputStream()

                val buff = ByteArray(1024)
                var count: Int
                while (true){
                    count = istream.read(buff, 0, 1024)
                    if(count==-1)break

                    os.write(buff, 0, count)
                }
                os.flush()
                os.close()

                payloadAsString = Utilities.bytesToStringUTFCustom(os.toByteArray(), os.toByteArray().size)
            }
            istream.close()
            is_.close()


        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        } catch (e1: IOException) {
            e1.printStackTrace()
        }

        if(payloadAsString==null)return

        var jsonReader = JsonReader(StringReader(payloadAsString))
        try {
            var ver: Int? = 0
            var type: String? = null
            var data: String? = null

            jsonReader.beginObject()
            while (jsonReader.hasNext()) {
                val paramName = jsonReader.nextName()
                when (paramName) {
                    "ver" -> ver = jsonReader.nextInt()
                    "type" -> type = jsonReader.nextString()
                    "data" -> data = jsonReader.nextString()
                }
            }
            jsonReader.endObject()
            jsonReader.close()

            if (type == TOPICS_DATA) {

                jsonReader = JsonReader(StringReader(data!!))
                jsonReader.beginArray()
                while (jsonReader.hasNext()) {
                    var topicName = ""
                    var payloadData = ""

                    jsonReader.beginObject()
                    while (jsonReader.hasNext()) {
                        val paramName = jsonReader.nextName()
                        when (paramName) {
                            "topic" -> topicName = jsonReader.nextString()
                            "payload" -> payloadData = jsonReader.nextString()
                        }
                    }
                    jsonReader.endObject()

                    //Log.d("servermode", "topicName="+topicName+"  payload"+payloadData);
                    processReceiveSimplyTopicPayloadData(topicName, payloadData)
                }
                jsonReader.endArray()

            }

        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    //OnReceive()
    private fun processOnReceiveEvent(topic: String?, payload: String?) {
        if(dashboards==null)return;
        if (!AppSettings.instance.server_mode) return

        for (dashboard in dashboards!!) {
            for (widgetData in dashboard.widgetsList) {
                if (widgetData.getSubTopic(0) != topic) continue

                val code = widgetData.onReceiveExecute
                if (code.isEmpty()) continue

                evalJS(widgetData, payload, code)
            }
        }
    }

    private fun processReceiveSimplyTopicPayloadData(topic: String, payload: String) {

        processOnReceiveEvent(topic, payload)

        notifyDataInTopicChanged(topic, payload)

        currentMQTTValues.put(topic, payload)
        currentDataVersion++

        if (push_topic != null && !push_topic!!.isEmpty()) {
            val pushTopicTemplate = push_topic!!.replace("/#".toRegex(), "")
            val templateSize = pushTopicTemplate.length
            if (topic.length >= templateSize && topic.substring(0, templateSize) == pushTopicTemplate) {
                val lastPush = lastReceivedMessagesByTopic!![topic]
                if (lastPush == null || lastPush != payload) {
                    lastReceivedMessagesByTopic!!.put(topic, payload)

                    if (topic.startsWith(serverPushNotificationTopicRootPath)) {
                        //расширенное сообщение с сервера приложения, нужно интерпретировать
                        if (payload != "") {
                            showPushNotification(topic, payload)
                        }

                    } else {
                        //обычный кусок текста, нужно показать
                        if (payload != "") {
                            showPushNotification(topic, payload)
                        }
                    }
                }
            }
        }
    }

    fun publishConfig(){

        val os_ = ByteArrayOutputStream();
        val os = ZipOutputStream(BufferedOutputStream(os_))

        val allSettings = AppSettings.instance.settingsAsStringForExport
        os.putNextEntry(ZipEntry("settings.json"))
        os.flush()
        os.write(Utilities.stringToBytesUTFCustom(allSettings))
        os.close()

        publishMQTTMessage(publishConfigTopicRootPath, MqttMessage(os_.toByteArray()))
    }

    companion object {

        internal val STATE_DISCONNECTED = 0
        internal val STATE_HALF_CONNECTED = 1
        internal val STATE_FULL_CONNECTED = 2

        //internal var clientCountsInForeground = 0

        var instance: MQTTService? = null

        /*
        private val FULL_VERSION_FOR_ALL = "full_version_for_all"
        private val MESSAGE_TITLE = "message_title"
        private val MESSAGE_TEXT = "message_text"
        private val AD_FREQUENCY = "ad_frequency"
        private val REBUILD_HISTORY_DATA_FREQUENCY = "rebuild_history_data_frequency"
        */

        internal val TOPICS_DATA = "topics_data"

        internal var mDbHelper: DbHelper? = null
        internal var db: SQLiteDatabase? = null


        internal var connectionInUnActualMode = false

        internal var wl: PowerManager.WakeLock? = null

        internal var inRealForegroundMode = false
        internal var idleTime = 0

    }


    var counter = 0
    private var timer: Timer? = null
    private var timerTask: TimerTask? = null
    internal var oldTime: Long = 0
    fun startTimer() {
        //set a new Timer
        timer = Timer()

        //initialize the TimerTask's job
        initializeTimerTask()

        //schedule the timer, to wake up every 1 second
        timer!!.schedule(timerTask, 1000, 1000) //
        Log.d("in timer", "start")
    }

    fun initializeTimerTask() {
        timerTask = object : TimerTask() {
            override fun run() {
                //Log.d("in timer", "in timer ++++  " + counter++)

            }
        }
    }

    fun stoptimertask() {
        //stop the timer, if it's not already null
        if (timer != null) {
            timer!!.cancel()
            timer = null
            Log.d("in timer", "stop")
        }
    }

    fun interactiveMode(isOn: Boolean) {
        needFullConnect=isOn
        Log.d("test", ""+needFullConnect)
        if(!needFullConnect){
            Log.d("test","WTF?")
        }

        Thread(Runnable {
            for(i in 1..30) { // 2 sec
                if(isConnected){
                    if(isOn){
                        subscribeForState(STATE_FULL_CONNECTED)
                    }else{
                        subscribeForState(STATE_HALF_CONNECTED)
                    }
                    break;
                }
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }).start()
    }
}
