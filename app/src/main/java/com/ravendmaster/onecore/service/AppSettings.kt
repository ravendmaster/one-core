package com.ravendmaster.onecore.service

import android.util.JsonReader
import android.util.Log
import com.ravendmaster.onecore.TabData
import com.ravendmaster.onecore.TabsCollection
import com.ravendmaster.onecore.Utilities
import org.json.JSONException
import org.json.JSONObject
import java.io.*


class AppSettings private constructor()
{
    var view_compact_mode = false;
    var view_magnify = 0
    var settingsVersion = 1
    var adfree = true
    var keep_alive = ""
    var server = ""
    var port = ""
    var username = ""
    var password = ""
    var server_topic: String = ""
    var server_mode: Boolean = false
    var push_notifications_subscribe_topic = ""
    var connection_in_background = false
    internal lateinit var dashboardsConfiguration: DashboardsConfiguration

    internal var tabs = TabsCollection()

    val tabNames: Array<String?>
        get() {
            val result = arrayOfNulls<String>(tabs!!.items.size)
            var index = 0
            for (tabData in tabs!!.items) {
                result[index++] = tabData.name
            }
            return result
        }

    private var settingsLoaded = false

    private val appSettingsAsString: String
        get() {

            val resultJson = JSONObject()

            try {
                resultJson.put("server", server)
                resultJson.put("port", port)
                resultJson.put("username", username)
                resultJson.put("server_topic", server_topic)
                resultJson.put("push_notifications_subscribe_topic", push_notifications_subscribe_topic)
                resultJson.put("keep_alive", keep_alive)
                resultJson.put("connection_in_background", connection_in_background)
                resultJson.put("settingsVersion", settingsVersion)
                resultJson.put("view_compact_mode", view_compact_mode)
                resultJson.put("view_magnify", view_magnify)

            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return resultJson.toString()
        }


    val settingsAsStringForExport: String
        get() {

            val resultJson = JSONObject()

            try {
                resultJson.put("server", server)
                resultJson.put("port", port)
                resultJson.put("username", username)
                resultJson.put("server_topic", server_topic)
                resultJson.put("push_notifications_subscribe_topic", push_notifications_subscribe_topic)
                resultJson.put("keep_alive", keep_alive)
                resultJson.put("connection_in_background", connection_in_background)
                resultJson.put("settingsVersion", settingsVersion)

                resultJson.put("tabs", tabs!!.asJSON)

                resultJson.put("dashboards", dashboardsConfiguration.asJSON)

            } catch (e: JSONException) {
                e.printStackTrace()
            }


            return resultJson.toString()
        }

    fun getDashboardIDByTabIndex(tabIndex: Int): Int {
        return tabs!!.getDashboardIdByTabIndex(tabIndex)
    }

    fun removeTabByDashboardID(id: Int) {
        tabs!!.removeByDashboardID(id)
    }

    fun addTab(tabData: TabData) {
        tabs!!.items.add(tabData)
    }

    fun readPrefsFromDisk() {

        if (settingsLoaded) return
        settingsLoaded = true

        Log.d(javaClass.name, "readPrefsFromDisk()")

        var app_settings_file=File(Utilities.getAppDir().absolutePath+ File.separator+"app_settings.json")
        if(app_settings_file.exists()) {
            setSettingsFromString(app_settings_file.readText())
        }

        if (server == "") {
            server = "ssl://m21.cloudmqtt.com"
            port = "26796"
            username = "ejoxlycf"
            password = "odhSFqxSDACF"
            //3.0 subscribe_topic = "out/wcs/#";
            push_notifications_subscribe_topic = "out/wcs/push_notifications/#"
            keep_alive = "60"
            connection_in_background = false
        }

        tabs = TabsCollection()
        var file=File(Utilities.getAppDir().absolutePath+ File.separator+"tabs.json")
        if(file.exists()) {
            var fileData = FileReader(file.absoluteFile)
            tabs!!.setFromJSON(JsonReader(fileData))
        }

        dashboardsConfiguration = DashboardsConfiguration()
        for (tabData in tabs.items) {
            val file=File(Utilities.getAppDir().absolutePath+ File.separator+"dashboard${tabData.id}.json")
            if(!file.exists())continue
            dashboardsConfiguration.put(tabData.id, file.readText())
        }


    }

    fun saveConnectionSettingsToPrefs() {
        Log.d(javaClass.name, "saveConnectionSettingsToPrefs()")

        FileWriter(Utilities.getAppDir().absolutePath+ File.separator+"app_settings.json").use({ file ->
            file.write(appSettingsAsString)
        })

        /*
        val sprefs = con.getSharedPreferences("mysettings", Context.MODE_PRIVATE)
        val ed = sprefs.edit()
        ed.putBoolean("view_compact_mode", view_compact_mode)
        ed.putInt("view_magnify", view_magnify)

        ed.putBoolean("adfree", adfree!!)
        ed.putString("connection_server", server)
        ed.putString("connection_port", port)
        ed.putString("connection_username", username)
        ed.putString("connection_password", password)
        //3.0 ed.putString("connection_subscribe_topic", subscribe_topic);
        ed.putString("connection_server_topic", server_topic)
        ed.putString("connection_push_notifications_subscribe_topic", push_notifications_subscribe_topic)
        ed.putString("keep_alive", keep_alive)
        ed.putBoolean("connection_in_background", connection_in_background)
        ed.putBoolean("server_mode", server_mode)

        ed.putInt("settingsVersion", settingsVersion)

        if (!ed.commit()) {
            Log.d(javaClass.name, "commit failure!!!")
        }
        */
    }

    fun saveTabsSettingsToPrefs() {

        FileWriter(Utilities.getAppDir().absolutePath+ File.separator+"tabs.json").use({ file ->
            file.write(tabs!!.asJSON.toString())
        })

        /*
        val sprefs = con!!.getSharedPreferences("mytabs", Context.MODE_PRIVATE)
        val ed = sprefs.edit()

        ed.putString("tabs", tabs!!.asJSON.toString())
        if (!ed.commit()) {
            Log.d(javaClass.name, "commit failure!!!")
        }
        */
    }

    fun setSettingsFromString(text: String) {


        settingsVersion = 0

        val jsonReader = JsonReader(StringReader(text))
        try {
            jsonReader.beginObject()
            while (jsonReader.hasNext()) {
                val name = jsonReader.nextName()
                when (name) {

                    "server" -> server = jsonReader.nextString()
                    "port" -> port = jsonReader.nextString()
                    "username" -> username = jsonReader.nextString()
                    "password" -> password = jsonReader.nextString()
                    "subscribe_topic" -> {
                        //subscribe_topic = jsonReader.nextString();
                        val trash: String = jsonReader.nextString()
                    }
                    "server_topic" -> server_topic = jsonReader.nextString()
                    "push_notifications_subscribe_topic" -> push_notifications_subscribe_topic = jsonReader.nextString()
                    "keep_alive" -> keep_alive = jsonReader.nextString()
                    "connection_in_background" -> connection_in_background = jsonReader.nextBoolean()
                    "settingsVersion" -> settingsVersion = jsonReader.nextInt()

                    "view_compact_mode" -> view_compact_mode = jsonReader.nextBoolean()
                    "view_magnify" -> view_magnify = jsonReader.nextInt()

                    "tabs" -> {
                        tabs!!.items.clear()
                        tabs!!.setFromJSON(jsonReader)
                    }

                    "dashboards" -> {
                        jsonReader.skipValue()
                        dashboardsConfiguration.setFromJSONRAWString(text)
                    }

                    else -> Log.d("not readed param! ", name)
                }
            }
            jsonReader.endObject()
            jsonReader.close()

        } catch (e: IOException) {
            e.printStackTrace()
        }

        if (settingsVersion == 0) {
            saveTabsSettingsToPrefs()
        }
    }


    private object Holder { val instance = AppSettings() }

    companion object {
        val instance: AppSettings by lazy { Holder.instance }
    }
}
