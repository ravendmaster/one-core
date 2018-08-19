package com.ravendmaster.onecore

import android.os.Environment
import android.view.View
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

import com.ravendmaster.onecore.customview.ButtonsSet
import com.ravendmaster.onecore.customview.Graph
import com.ravendmaster.onecore.customview.Meter
import com.ravendmaster.onecore.customview.MyButton
import com.ravendmaster.onecore.customview.RGBLEDView
import java.io.File

import java.math.BigInteger
import java.util.UUID

object Utilities {

    fun getAppDir():File{

        val appDir="OneCore"
        val dir = File(Environment.getExternalStorageDirectory().absolutePath+ File.separator + appDir);
        if(!dir.exists()) {
            dir.mkdir()
        }
        return dir
    }

    fun onBindDragTabView(clickedView: View, dragView: View) {
        (dragView.findViewById<View>(R.id.tab_name) as TextView).text = (clickedView.findViewById<View>(R.id.tab_name) as TextView).text
    }

    fun onBindDragWidgetView(clickedView: View, dragView: View) {
        dragView.findViewById<View>(R.id.root).layoutParams.height=clickedView.findViewById<View>(R.id.root).layoutParams.height
        dragView.findViewById<View>(R.id.widget_drag_place).layoutParams.width=clickedView.findViewById<View>(R.id.widget_drag_place).layoutParams.width


        dragView.findViewById<View>(R.id.root).visibility = clickedView.findViewById<View>(R.id.root).visibility

        (dragView.findViewById<View>(R.id.widget_name) as TextView).text = (clickedView.findViewById<View>(R.id.widget_name) as TextView).text
        (dragView.findViewById<View>(R.id.widget_topic) as TextView).text = (clickedView.findViewById<View>(R.id.widget_topic) as TextView).text
        (dragView.findViewById<View>(R.id.widget_value) as TextView).text = (clickedView.findViewById<View>(R.id.widget_value) as TextView).text
        dragView.findViewById<View>(R.id.widget_value).visibility = clickedView.findViewById<View>(R.id.widget_value).visibility
        dragView.findViewById<View>(R.id.widget_value1).visibility = clickedView.findViewById<View>(R.id.widget_value1).visibility
        dragView.findViewById<View>(R.id.widget_value2).visibility = clickedView.findViewById<View>(R.id.widget_value2).visibility
        dragView.findViewById<View>(R.id.widget_value3).visibility = clickedView.findViewById<View>(R.id.widget_value3).visibility


        (dragView.findViewById<View>(R.id.widget_value) as TextView).setTextColor((clickedView.findViewById<View>(R.id.widget_value) as TextView).textColors)

        (dragView.findViewById<View>(R.id.widget_meter) as Meter).mode = (clickedView.findViewById<View>(R.id.widget_meter) as Meter).mode
        (dragView.findViewById<View>(R.id.widget_meter) as Meter).visibility = (clickedView.findViewById<View>(R.id.widget_meter) as Meter).visibility
        (dragView.findViewById<View>(R.id.widget_meter) as Meter).value = (clickedView.findViewById<View>(R.id.widget_meter) as Meter).value
        (dragView.findViewById<View>(R.id.widget_meter) as Meter).min = (clickedView.findViewById<View>(R.id.widget_meter) as Meter).min
        (dragView.findViewById<View>(R.id.widget_meter) as Meter).max = (clickedView.findViewById<View>(R.id.widget_meter) as Meter).max

        (dragView.findViewById<View>(R.id.widget_button) as MyButton).visibility = (clickedView.findViewById<View>(R.id.widget_button) as MyButton).visibility
        (dragView.findViewById<View>(R.id.widget_button) as MyButton).setColorLight((clickedView.findViewById<View>(R.id.widget_button) as MyButton).getColorLight())
        (dragView.findViewById<View>(R.id.widget_button) as MyButton).setLabelOff((clickedView.findViewById<View>(R.id.widget_button) as MyButton).getLabelOff())
        (dragView.findViewById<View>(R.id.widget_button) as MyButton).setLabelOn((clickedView.findViewById<View>(R.id.widget_button) as MyButton).getLabelOn())

        (dragView.findViewById<View>(R.id.widget_buttons_set) as ButtonsSet).visibility = (clickedView.findViewById<View>(R.id.widget_buttons_set) as ButtonsSet).visibility
        (dragView.findViewById<View>(R.id.widget_buttons_set) as ButtonsSet).setPublishValues((clickedView.findViewById<View>(R.id.widget_buttons_set) as ButtonsSet).getPublishValues())
        (dragView.findViewById<View>(R.id.widget_buttons_set) as ButtonsSet).setColorLight((clickedView.findViewById<View>(R.id.widget_buttons_set) as ButtonsSet).getColorLight())
        (dragView.findViewById<View>(R.id.widget_buttons_set) as ButtonsSet).setMaxButtonsPerRow((clickedView.findViewById<View>(R.id.widget_buttons_set) as ButtonsSet).getMaxButtonsPerRow())
        (dragView.findViewById<View>(R.id.widget_buttons_set) as ButtonsSet).setSize((clickedView.findViewById<View>(R.id.widget_buttons_set) as ButtonsSet).getSize())

        (dragView.findViewById<View>(R.id.widget_switch) as Switch).visibility = (clickedView.findViewById<View>(R.id.widget_switch) as Switch).visibility
        (dragView.findViewById<View>(R.id.widget_switch) as Switch).isChecked = (clickedView.findViewById<View>(R.id.widget_switch) as Switch).isChecked
        (dragView.findViewById<View>(R.id.widget_switch) as Switch).isEnabled = (clickedView.findViewById<View>(R.id.widget_switch) as Switch).isEnabled

        dragView.findViewById<View>(R.id.seek_bar_group).visibility = clickedView.findViewById<View>(R.id.seek_bar_group).visibility
        (dragView.findViewById<View>(R.id.widget_seekBar) as SeekBar).progress = (clickedView.findViewById<View>(R.id.widget_seekBar) as SeekBar).progress
        (dragView.findViewById<View>(R.id.widget_seekBar) as SeekBar).max = (clickedView.findViewById<View>(R.id.widget_seekBar) as SeekBar).max
        (dragView.findViewById<View>(R.id.widget_RGBLed) as RGBLEDView).visibility = (clickedView.findViewById<View>(R.id.widget_RGBLed) as RGBLEDView).visibility

        dragView.findViewById<View>(R.id.imageView_edit_button).visibility = clickedView.findViewById<View>(R.id.imageView_edit_button).visibility

        dragView.findViewById<View>(R.id.widget_graph).visibility = clickedView.findViewById<View>(R.id.widget_graph).visibility

        for (i in 0..3) {
            (dragView.findViewById<View>(R.id.widget_graph) as Graph).setValue(i, (clickedView.findViewById<View>(R.id.widget_graph) as Graph).getValue(i))
            (dragView.findViewById<View>(R.id.widget_graph) as Graph).setColorLight(i, (clickedView.findViewById<View>(R.id.widget_graph) as Graph).getColorLight(i))
            (dragView.findViewById<View>(R.id.widget_graph) as Graph).setName(i, (clickedView.findViewById<View>(R.id.widget_graph) as Graph).getName(i))
        }

        (dragView.findViewById<View>(R.id.widget_graph) as Graph).mode = (clickedView.findViewById<View>(R.id.widget_graph) as Graph).mode

        dragView.findViewById<View>(R.id.imageView_combo_box_selector).visibility = clickedView.findViewById<View>(R.id.imageView_combo_box_selector).visibility

        dragView.findViewById<View>(R.id.imageView_js).visibility = clickedView.findViewById<View>(R.id.imageView_js).visibility
    }

    fun createUUIDByString(uidString: String): UUID {
        val s2 = uidString.replace("-", "")
        return UUID(
                BigInteger(s2.substring(0, 16), 16).toLong(),
                BigInteger(s2.substring(16), 16).toLong())
    }

    fun parseInt(input: String, def: Int): Int {
        try {
            return Integer.parseInt(input)
        } catch (e: Exception) {
        }

        return def
    }

    fun parseFloat(input: String, def: Int): Float {
        try {
            return java.lang.Float.parseFloat(input)
        } catch (e: Exception) {
        }

        return def.toFloat()
    }

    fun round(input: Float): Float {
        return Math.round(input * 1000f) / 1000f
    }

    fun stringToBytesUTFCustom(str: String): ByteArray {
        return str.toByteArray()
    }

    fun bytesToStringUTFCustom(bytes: ByteArray, count: Int): String {
        return String(bytes)
    }

}
