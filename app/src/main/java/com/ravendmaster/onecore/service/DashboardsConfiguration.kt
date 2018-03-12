package com.ravendmaster.onecore.service

import android.util.SparseArray
import com.ravendmaster.onecore.activity.MainActivity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class DashboardsConfiguration {

    private var items = SparseArray<String>()

    val asJSON: JSONArray
        get() {
            val dashboards = JSONArray()
            for (tabData in MainActivity.getPresenter()!!.tabs!!.items) {
                val dashboard = JSONObject()
                try {
                    dashboard.put("id", tabData.id.toString())
                    dashboard.put("dashboard", JSONArray(items[tabData.id]))
                } catch (e: JSONException) {
                    e.printStackTrace()
                }

                dashboards.put(dashboard)
            }
            return dashboards
        }

    fun put(name: Int, data: String) {
        items.put(name, data)
    }

    operator fun get(name: Int): String {
        return items[name].toString()
    }

    internal fun setFromJSONRAWString(RawJSON: String) {
        items.clear()

        try {
            val jsonObj = JSONObject(RawJSON)
            val dashboards = jsonObj.getJSONArray("dashboards")
            val dashboardsCount = dashboards.length()
            for (i in 0 until dashboardsCount) {
                val id: Int?
                var data = ""
                val dashboard = dashboards.getJSONObject(i)
                id = dashboard.getInt("id")
                if (!dashboard.isNull("dashboard"))
                    data = dashboard.getJSONArray("dashboard").toString()
                items.put(id, data)
            }
        } catch (e: Exception) {
            android.util.Log.d("error", e.toString())
        }

    }


}
