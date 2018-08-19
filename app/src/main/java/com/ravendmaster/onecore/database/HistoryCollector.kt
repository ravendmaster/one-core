package com.ravendmaster.onecore.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log

import java.util.Date

class HistoryCollector
//private String[] topicsList;

(internal var db: SQLiteDatabase?) {
    internal var topicsCollector: TopicsCollector

    internal val DETAIL_LEVEL_RAW = 0
    internal val DETAIL_LEVEL_HOUR = 10

    var needCollectData = false

    val topicsList: Array<Any>
        get() = topicsCollector.topicNames

    init {
        this.topicsCollector = TopicsCollector(db)
        this.topicsCollector.load()
    }

    fun getTopicIDByName(topic: String): Long? {
        return topicsCollector.getIdForTopic(topic)
    }

    fun collect(topic: String, value: String) {
        if (!needCollectData) return
        val topicId = getTopicIDByName(topic)!!//topicsCollector.getIdForTopic(topic);

        if (value.equals("\$clear", ignoreCase = true)) {
            db!!.delete(HistoryContract.HistoryEntry.TABLE_NAME, HistoryContract.HistoryEntry.COLUMN_NAME_TOPIC_ID + "=" + topicId, null)
            return
        }

        Log.d("servermode", "save:$topic=$value")
        val date = Date()
        val values = ContentValues()
        values.put(HistoryContract.HistoryEntry.COLUMN_NAME_TIMESTAMP, date.time)
        values.put(HistoryContract.HistoryEntry.COLUMN_NAME_DETAIL_LEVEL, DETAIL_LEVEL_RAW)
        values.put(HistoryContract.HistoryEntry.COLUMN_NAME_TOPIC_ID, topicId)
        values.put(HistoryContract.HistoryEntry.COLUMN_NAME_VALUE, value)
        db!!.insert(HistoryContract.HistoryEntry.TABLE_NAME, null, values)


    }


}
