package com.ravendmaster.onecore.service

import android.util.JsonReader

import com.ravendmaster.onecore.Utilities
import com.ravendmaster.onecore.customview.Graph
import com.ravendmaster.onecore.customview.MyColors

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.util.*

class Dashboard(var id: Int) {

    val widgetsList: ArrayList<WidgetData>

    init {
        widgetsList = ArrayList()
    }

    fun findWidgetByTopic(topic: String): WidgetData? {
        for (widgetData in widgetsList) {
            if (widgetData.getSubTopic(0) == topic) return widgetData
        }
        return null
    }

    fun clear() {
        widgetsList.clear()
    }

    fun saveDashboardToDisk() {
        val ar = JSONArray()
        for (widget in widgetsList) {
            val resultJson = JSONObject()
            try {
                resultJson.put("type", widget.type)
                resultJson.put("name", widget.getName(0))
                resultJson.put("name1", widget.getName(1))
                resultJson.put("name2", widget.getName(2))
                resultJson.put("name3", widget.getName(3))

                resultJson.put("publish", widget.publishTopic)
                resultJson.put("subscribe", widget.subscribeTopic)

                resultJson.put("topic", widget.getSubTopic(0))
                resultJson.put("topic1", widget.getSubTopic(1))
                resultJson.put("topic2", widget.getSubTopic(2))
                resultJson.put("topic3", widget.getSubTopic(3))

                resultJson.put("pubTopic", widget.getPubTopic(0))
                resultJson.put("pubTopic1", widget.getPubTopic(1))
                resultJson.put("pubTopic2", widget.getPubTopic(2))
                resultJson.put("pubTopic3", widget.getPubTopic(3))

                resultJson.put("publishValue", widget.publishValue)
                resultJson.put("publishValue2", widget.publishValue2)

                resultJson.put("primaryColor", widget.getPrimaryColor(0))
                resultJson.put("primaryColor1", widget.getPrimaryColor(1))
                resultJson.put("primaryColor2", widget.getPrimaryColor(2))
                resultJson.put("primaryColor3", widget.getPrimaryColor(3))

                resultJson.put("feedback", widget.feedback) //устарел
                resultJson.put("label", widget.label)
                resultJson.put("label2", widget.label2)
                resultJson.put("retained", widget.retained)
                resultJson.put("additionalValue", widget.additionalValue)
                resultJson.put("additionalValue2", widget.additionalValue2)
                resultJson.put("additionalValue3", widget.additionalValue3)
                resultJson.put("decimalMode", widget.decimalMode)
                resultJson.put("mode", widget.mode)
                resultJson.put("onShowExecute", widget.onShowExecute)
                resultJson.put("onReceiveExecute", widget.onReceiveExecute)
                resultJson.put("formatMode", widget.formatMode)
                resultJson.put("uid", widget.uid)
            } catch (e: JSONException) {
                e.printStackTrace()
            }

            ar.put(resultJson)
        }

        val JSONText=ar.toString()
        AppSettings.instance.dashboardsConfiguration.put(id, JSONText)

        FileWriter(Utilities.getAppDir().absolutePath+ File.separator+"dashboard${id}.json").use({ file ->
            file.write(JSONText)
        })
    }

    fun createDashboardByJSON(data: String){
        val jsonReader = JsonReader(StringReader(data))

        try {
            //TODO данных может не быть
            jsonReader.beginArray()
            while (jsonReader.hasNext()) {

                val widget = WidgetData()

                jsonReader.beginObject()
                while (jsonReader.hasNext()) {
                    val name = jsonReader.nextName()
                    when (name) {
                        "type" -> {
                            val type_text = jsonReader.nextString()
                            if (type_text == "VALUE") {
                                widget.type = WidgetData.WidgetTypes.VALUE
                            } else if (type_text == "SWITCH") {
                                widget.type = WidgetData.WidgetTypes.SWITCH
                            } else if (type_text == "BUTTON") {
                                widget.type = WidgetData.WidgetTypes.BUTTON
                            } else if (type_text == "RGBLed") {
                                widget.type = WidgetData.WidgetTypes.RGBLed
                            } else if (type_text == "SLIDER") {
                                widget.type = WidgetData.WidgetTypes.SLIDER
                            } else if (type_text == "HEADER") {
                                widget.type = WidgetData.WidgetTypes.HEADER
                            } else if (type_text == "METER") {
                                widget.type = WidgetData.WidgetTypes.METER
                            } else if (type_text == "GRAPH") {
                                widget.type = WidgetData.WidgetTypes.GRAPH
                            } else if (type_text == "BUTTONSSET") {
                                widget.type = WidgetData.WidgetTypes.BUTTONSSET
                            } else if (type_text == "COMBOBOX") {
                                widget.type = WidgetData.WidgetTypes.COMBOBOX
                            } else
                                Exception("Error!")
                        }
                        "name" -> widget.setName(0, jsonReader.nextString())
                        "name1" -> widget.setName(1, jsonReader.nextString())
                        "name2" -> widget.setName(2, jsonReader.nextString())
                        "name3" -> widget.setName(3, jsonReader.nextString())
                        "publish" -> widget.publishTopic = jsonReader.nextString()
                        "subscribe" -> widget.subscribeTopic = jsonReader.nextString()
                        "publishValue" -> widget.publishValue = jsonReader.nextString()

                        "topic" -> widget.setSubTopic(0, jsonReader.nextString())
                        "topic1" -> widget.setSubTopic(1, jsonReader.nextString())
                        "topic2" -> widget.setSubTopic(2, jsonReader.nextString())
                        "topic3" -> widget.setSubTopic(3, jsonReader.nextString())

                        "pubTopic" -> widget.setPubTopic(0, jsonReader.nextString())
                        "pubTopic1" -> widget.setPubTopic(1, jsonReader.nextString())
                        "pubTopic2" -> widget.setPubTopic(2, jsonReader.nextString())
                        "pubTopic3" -> widget.setPubTopic(3, jsonReader.nextString())

                        "publishValue2" -> widget.publishValue2 = jsonReader.nextString()

                        "primaryColor" -> widget.setPrimaryColor(0, jsonReader.nextInt())
                        "primaryColor1" -> widget.setPrimaryColor(1, jsonReader.nextInt())
                        "primaryColor2" -> widget.setPrimaryColor(2, jsonReader.nextInt())
                        "primaryColor3" -> widget.setPrimaryColor(3, jsonReader.nextInt())
                        "feedback" -> widget.feedback = jsonReader.nextBoolean()
                        "label" -> widget.label = jsonReader.nextString()
                        "label2" -> widget.label2 = jsonReader.nextString()
                        "newValueTopic" -> jsonReader.nextString() //stub
                        "retained" -> widget.retained = jsonReader.nextBoolean()
                        "additionalValue" -> widget.additionalValue = jsonReader.nextString()
                        "additionalValue2" -> widget.additionalValue2 = jsonReader.nextString()
                        "additionalValue3" -> widget.additionalValue3 = jsonReader.nextString()
                        "decimalMode" -> widget.decimalMode = jsonReader.nextBoolean()
                        "mode" -> widget.mode = jsonReader.nextInt()
                        "onShowExecute" -> widget.onShowExecute = jsonReader.nextString()
                        "onReceiveExecute" -> widget.onReceiveExecute = jsonReader.nextString()
                        "formatMode" -> widget.formatMode = jsonReader.nextString()
                        "uid" -> widget.uid = UUID.fromString(jsonReader.nextString())
                    }
                }
                jsonReader.endObject()
                widgetsList.add(widget)
            }
            jsonReader.endArray()
            jsonReader.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun updateFromSettings() {
        widgetsList.clear()
        val data = AppSettings.instance.dashboardsConfiguration.get(id)
        createDashboardByJSON(data)
    }

    fun initDemoDashboard() {
        widgetsList.clear()
        widgetsList.add(WidgetData(WidgetData.WidgetTypes.HEADER, "Value example", "", "", "", 0, ""))
        widgetsList.add(WidgetData(WidgetData.WidgetTypes.VALUE, "Server time (Value)", "out/wcs/time", "", "", MyColors.ltGray, ""))

        widgetsList.add(WidgetData(WidgetData.WidgetTypes.HEADER, "Graph (JSON array of double/integer)", "", "", "", 0, ""))

        var wd = WidgetData(WidgetData.WidgetTypes.GRAPH, "Source sin(x), refresh in 3 seconds", "out/wcs/graph", "", "", MyColors.blue, "")
        wd.mode = Graph.WITHOUT_HISTORY
        widgetsList.add(wd)

        widgetsList.add(WidgetData(WidgetData.WidgetTypes.HEADER, "RGB LED, Switch and Button example", "", "", "", 0, ""))
        widgetsList.add(WidgetData(WidgetData.WidgetTypes.RGBLed, "Valves are opened (RGB LED)", "out/wcs/v0", "1", "0", MyColors.green, ""))
        widgetsList.add(WidgetData(WidgetData.WidgetTypes.RGBLed, "Valves are closed (inverted input)", "out/wcs/v0", "0", "1", MyColors.red, ""))

        widgetsList.add(WidgetData(WidgetData.WidgetTypes.SWITCH, "Valves (Switch)", "out/wcs/v0", "1", "0", 0, ""))

        widgetsList.add(WidgetData(WidgetData.WidgetTypes.BUTTON, "Open valves (Button)", "out/wcs/v0", "1", "", MyColors.green, "OPEN", "", true))
        widgetsList.add(WidgetData(WidgetData.WidgetTypes.BUTTON, "Close valves (Button)", "out/wcs/v0", "0", "", MyColors.red, "CLOSE", "", true))

        widgetsList.add(WidgetData(WidgetData.WidgetTypes.HEADER, "Slider and Meter example", "", "", "", 0, ""))
        widgetsList.add(WidgetData(WidgetData.WidgetTypes.METER, "Light (Meter)", "out/wcs/slider", "0", "255", 0, "").setAdditionalValues("30", "0"))
        widgetsList.add(WidgetData(WidgetData.WidgetTypes.VALUE, "Light (Value)", "out/wcs/slider", "0", "", MyColors.ltGray, "out/wcs/slider"))
        widgetsList.add(WidgetData(WidgetData.WidgetTypes.SLIDER, "Light (Slider)", "out/wcs/slider", "0", "255", 0, ""))

        wd = WidgetData(WidgetData.WidgetTypes.GRAPH, "Source - Light (Slider)", "out/wcs/slider", "", "", MyColors.red, "")
        wd.mode = Graph.LIVE
        widgetsList.add(wd)

        widgetsList.add(WidgetData(WidgetData.WidgetTypes.HEADER, "RGB LED all modes(on/off/#rrggbb)", "", "", "", 0, ""))
        widgetsList.add(WidgetData(WidgetData.WidgetTypes.RGBLed, "RGB LED (default is red)", "out/wcs/rgbled_test", "ON", "OFF", MyColors.red, ""))

        widgetsList.add(WidgetData(WidgetData.WidgetTypes.BUTTONSSET, "LED Modes (Buttons set)", "out/wcs/rgbled_test", "ON,OFF,,,#ff5555|red,#55ff55|green,#5555ff|blue", "", MyColors.ltGray, "post 'ON'", "", true))

        widgetsList.add(WidgetData(WidgetData.WidgetTypes.COMBOBOX, "LED Modes (Combobox)", "out/wcs/rgbled_test", "ON,OFF,#ff5555|red,#55ff55|green,#5555ff|blue", "", MyColors.ltGray, "post 'ON'", "", true))
    }


}
