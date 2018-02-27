package com.ravendmaster.onecore.service

import com.ravendmaster.onecore.Log
import com.ravendmaster.onecore.Utilities
import org.fusesource.hawtbuf.Buffer
import org.fusesource.hawtbuf.UTF8Buffer
import org.fusesource.mqtt.client.*

import java.net.URISyntaxException
import java.util.ArrayList
import org.fusesource.mqtt.codec.MQTTFrame



class CallbackMQTTClient(internal var imqttMessageReceiver: IMQTTMessageReceiver) {

    private var mqtt: MQTT = MQTT()
    private var callbackConnection : CallbackConnection? = null

    var isConnected: Boolean = false
        internal set

    init {
        mqtt.keepAlive = 10
        mqtt.reconnectDelay = 1000
        mqtt.reconnectDelayMax = 3000
        mqtt.reconnectBackOffMultiplier = 1.0
        mqtt.reconnectAttemptsMax
    }

    interface IMQTTMessageReceiver {
        fun onReceiveMQTTMessage(topic: String, payload: Buffer)
    }

    fun publish(topic: String, payload: Buffer, retained: Boolean) {

        if (callbackConnection == null ) {
            isConnected = false
            return
        }
        Log.d(javaClass.name, "publish() "+topic)

        callbackConnection!!.publish(topic, payload.toByteArray(), QoS.AT_LEAST_ONCE, retained, object : Callback<Void> {

            override fun onSuccess(p0: Void?) {
                isConnected = true
                //Log.d(javaClass.name, "PUBLISH SUCCESS")
            }

            override fun onFailure(value: Throwable?) {
                isConnected = false
                Log.d(javaClass.name, "PUBLISH FAILED!!! " + value.toString())
            }
        })
    }


    fun subscribe2(topic: String) {

        Log.d("test", "subscribe():" + topic)
        if ((callbackConnection == null) || topic.isEmpty()) return
            try {
                callbackConnection!!.subscribe(arrayOf(Topic(topic, QoS.AT_LEAST_ONCE)), object : Callback<ByteArray> {
                    override fun onSuccess(bytes: ByteArray?) {
                    }

                    override fun onFailure(throwable: Throwable?) {
                        Log.d(javaClass.name, "subscribe failed!!! " + throwable.toString())
                    }
                })
            }
            catch (e:Exception){

            }
    }

    /*
    fun subscribeMass_(topicsList: ArrayList<String>) {
        if(callbackConnection==null)return
        val topics = arrayOfNulls<Topic>(topicsList.size)//{new Topic(topic, QoS.AT_LEAST_ONCE)};
        var index = 0
        for (topic in topicsList) {
            topics[index++] = Topic(topic, QoS.AT_LEAST_ONCE)
            Log.d(javaClass.name, "subscribeMass():" + topic)
        }

        callbackConnection!!.subscribe(topics, object : Callback<ByteArray> {
            override fun onSuccess(bytes: ByteArray) {
            }
            override fun onFailure(throwable: Throwable) {
                Log.d(javaClass.name, "subscribe failed!!! " + throwable.toString())
            }
        })

    }

    fun unsubscribeMass_(topicsList: ArrayList<String>) {
        if(callbackConnection==null)return
        val topics = arrayOfNulls<UTF8Buffer>(topicsList.size)// {new UTF8Buffer(topic)};
        var index = 0
        for (topic in topicsList) {
            topics[index++] = UTF8Buffer(topic)
            Log.d(javaClass.name, "unsubscribeMass():" + topic)
        }

        callbackConnection!!.unsubscribe(topics, object : Callback<Void> {
            override fun onSuccess(aVoid: Void?) {}

            override fun onFailure(throwable: Throwable?) {
                Log.d("test", "unsubscribe failed!!! " + throwable.toString())
            }
        })
    }
    */

    fun unsubscribe2(topic: String?) {
        Log.d("test", "unsubscribe():" + topic!!)
        if (callbackConnection == null || topic.isEmpty()) return
        val topics = arrayOf(UTF8Buffer(topic))
        try {
            callbackConnection!!.unsubscribe(topics, object : Callback<Void> {
                override fun onSuccess(aVoid: Void?) {}

                override fun onFailure(throwable: Throwable?) {
                    Log.d("test", "unsubscribe failed!!! " + throwable.toString())
                }
            })
        }catch(e:Exception){

        }
    }


    fun disconnect() {
        if (!isConnected) return
        Log.d("test", "DISCONNECT")
        isConnected = false
        if (callbackConnection != null) {
            //Log.d(javaClass.name, "callbackConnection.disconnect()")

            callbackConnection!!.disconnect(object : Callback<Void> {
                override fun onSuccess(aVoid: Void?) {
                    //wait = false;
                    Log.d("test", "disconnect success")
                }

                override fun onFailure(throwable: Throwable?) {
                    Log.d("test", "disconnect failed!!!" + throwable.toString())
                }
            })

        }
    }

    fun connect(settings: AppSettings) {
        Log.d("test", "CONNECT!!! " + this.toString())
        mqtt.isCleanSession = true

        mqtt.setUserName(settings.username)
        mqtt.setPassword(settings.password)

        try {
            if (settings.server.indexOf("://") == -1) {
                mqtt.setHost(settings.server, Utilities.parseInt(settings.port, 1883))
            } else {
                mqtt.setHost(settings.server + ":" + Utilities.parseInt(settings.port, 1883))
            }

        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }

        mqtt.tracer = object : Tracer() {

            override fun onReceive(frame: MQTTFrame) {
                println("recv: " + frame)
                Log.d(javaClass.name+" Trace", "recv: "+frame)
            }

            override fun onSend(frame: MQTTFrame) {
                Log.d(javaClass.name+" Trace", "send: " + frame)
            }

            override fun debug(message: String, vararg args: Any) {
                Log.d(javaClass.name+" Trace", String.format("debug: " + message, *args))
            }
        }



        Log.d(javaClass.name, "callbackConnection = mqtt.callbackConnection();")

        callbackConnection = mqtt.callbackConnection()

        callbackConnection!!.listener(object : Listener {
            override fun onConnected() {
                isConnected = true
                Log.d(javaClass.name, "callbackConnection.listener onConnected()")
            }

            override fun onDisconnected() {
                isConnected = false
                Log.d(javaClass.name, "callbackConnection.listener onDisconnected()")
            }

            override fun onPublish(topic: UTF8Buffer, payload: Buffer, ack: Runnable) {
                ack.run()

                isConnected = true

                var urbanTopic = topic.toString()
                if (urbanTopic[urbanTopic.length - 1] == '$') {
                    urbanTopic = urbanTopic.substring(0, urbanTopic.length - 1)
                }
                Log.d(javaClass.name, "onPublish "+urbanTopic+" payload:"+String(payload.toByteArray()));
                imqttMessageReceiver.onReceiveMQTTMessage(urbanTopic, payload)
            }

            override fun onFailure(throwable: Throwable) {
                Log.d(javaClass.name, "callbackConnection.listener onFailure() " + throwable.toString())
            }
        })


        callbackConnection!!.connect(object : Callback<Void> {
            override fun onSuccess(Void: Void?) {
                isConnected = true
                Log.d(javaClass.name, "callbackConnection.reConnect onSuccess()")

                subscribe2("#")
            }

            override fun onFailure(throwable: Throwable?) {

            }
        })

    }

}
