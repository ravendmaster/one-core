package com.ravendmaster.onecore.customview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View

import com.ravendmaster.onecore.activity.MainActivity
import com.ravendmaster.onecore.service.AppSettings
import com.ravendmaster.onecore.service.Presenter

import java.util.ArrayList

class MyTabsController(context: Context, attrs: AttributeSet) : View(context, attrs) {

    internal var bounds = Rect()

    internal var x_dispose = 0

    val tabs = ArrayList<MyTab>()

    private var selectedScreenTabIndex: Int = 0

    internal var widget_width: Int = 0


    internal var startedMove = false

    internal var pressed = false
    internal var started_x: Int = 0

    internal var pressedTabIndex: Int = 0

    internal var startMoveXPos: Int = 0


    internal var tabButtonWidth : Int

    inner class MyTab(var name: String, var dashboardName: Int)


    fun refreshState(context: Context) {

        tabs.clear()

        if (isInEditMode) {
            tabs.add(MyTab("tab0", 0))
            tabs.add(MyTab("tab1", 1))
            selectedScreenTabIndex = 0
        } else {
            //Log.d("dashboard orders", "---------------------");
            val tabsCollection = MainActivity.getPresenter().tabs
            if (tabsCollection != null) {
                for (tab in tabsCollection.items) {
                    //Log.d("dashboard orders", "" + tab.id + "  " + tab.name);
                    tabs.add(MyTab(tab.name, tab.id))
                }
            }


            /*
            AppSettings settings = AppSettings.getInstance();
            settings.readPrefsFromDisk(getContext());
            tabs.add(new MyTab(settings.tabs[0], 0));
            if (!settings.tabs[1].equals("")) tabs.add(new MyTab(settings.tabs[1], 1));
            if (!settings.tabs[2].equals("")) tabs.add(new MyTab(settings.tabs[2], 2));
            if (!settings.tabs[3].equals("")) tabs.add(new MyTab(settings.tabs[3], 3));
            */


            selectedScreenTabIndex = MainActivity.getPresenter().screenActiveTabIndex
        }

        invalidate()
        requestLayout()
    }

    var interfaceMagnify = 0f

    init {

        refreshState(context)

        interfaceMagnify = 1.0f + AppSettings.instance.view_magnify.toFloat() / 4

        p.isAntiAlias = true
        p.textSize = 20f * interfaceMagnify;

        tabButtonWidth = (192f * interfaceMagnify).toInt()

        selectedScreenTabIndex = 0

        setLayerToHW(this)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var heightMeasureSpec = heightMeasureSpec

        // Try for a width based on our minimum
        val minw = paddingLeft + paddingRight + suggestedMinimumWidth
        val w = View.resolveSizeAndState(minw, widthMeasureSpec, 1)

        val mTextWidth = 200
        // Whatever the width ends up being, ask for a height that would let the pie
        // get as big as it can
        val minh = View.MeasureSpec.getSize(w) - mTextWidth.toInt() + paddingBottom + paddingTop
        val h = View.resolveSizeAndState(minh, heightMeasureSpec, 1)

        if (tabs.size <= 1) {
            heightMeasureSpec = 0
        }

        widget_width = widthMeasureSpec
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
    }

    internal fun refreshXDispose() {
        x_dispose = Math.min(tabs.size * tabButtonWidth - (right - left), x_dispose)
        x_dispose = Math.max(0, x_dispose)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        super.onTouchEvent(event)
        if (!isEnabled) return true

        val left = left
        val right = right
        val width = right - left
        val tabWidth = tabButtonWidth//width / tabs.size();

        //Log.d(getClass().getName(), event.toString());
        val X = event.x.toInt()
        val Y = event.y.toInt()
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                startedMove = true
                x_dispose += startMoveXPos - X

                refreshXDispose()

                startMoveXPos = X
                invalidate()
            }
            MotionEvent.ACTION_DOWN -> {
                started_x = X
                pressedTabIndex = (X + x_dispose) / tabWidth
                startedMove = false
                startMoveXPos = X
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = false
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                pressed = false
                if (Math.abs(started_x - X) < 20) {
                    selectedScreenTabIndex = (X + x_dispose) / tabWidth
                    selectedScreenTabIndex = Math.min(selectedScreenTabIndex!!, tabs.size - 1)

                    playSoundEffect(SoundEffectConstants.CLICK)
                    //MyTab selectedTab = tabs.get(selectedScreenTabIndex);
                    MainActivity.getPresenter().onTabPressed(selectedScreenTabIndex!!)
                    //Log.d("ashboard orders", "tap " + selectedScreenTabIndex);
                    invalidate()
                }
            }
        }

        return true
    }

    private fun setLayerToHW(v: View) {
        if (!v.isInEditMode && Build.VERSION.SDK_INT >= 11) {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
    }

    override fun onDraw(canvas: Canvas) {

        if (tabs.size == 0) return

        refreshXDispose()

        //canvas.drawColor(0xFFFF00) //серый фон

        val left = left
        val right = right
        val top = top
        val bottom = bottom

        val width = right - left
        val tabWidth = tabButtonWidth //width / tabs.size();
        val tabHeight = bottom - top

        if (pressed) {
            val pressedTabX = tabWidth * pressedTabIndex - x_dispose
            p.color = MyColors.yellow
            canvas.drawRect(pressedTabX.toFloat(), 0f, (pressedTabX + tabWidth).toFloat(), tabHeight.toFloat(), p)
        }


        p.color = MyColors.white
        for (i in tabs.indices) {
            val tabX = tabWidth * i
            val tab = tabs[i]
            val name = tab.name
            //primary_paint.getTextBounds(name, 0, name.length(), bounds);
            //canvas.drawText(name, tabX + tabWidth / 2 - (bounds.right - bounds.left) / 2, tabHeight / 2 + (bounds.bottom - bounds.top) / 2, primary_paint);


            setBoundsOfThreeDots()
            val widthOfThreeDots = bounds.right - bounds.left
            var text = name.toUpperCase()
            val measuredWidth = FloatArray(100)
            val cnt = p.breakText(text, true, (tabWidth - widthOfThreeDots - 4).toFloat(), measuredWidth)

            if (cnt < text.length) {
                text = text.substring(0, cnt)
                text += "..."
            }
            p.getTextBounds(text, 0, text.length, bounds)
            canvas.drawText(text, (tabX + tabWidth / 2 - (bounds.right - bounds.left) / 2 - x_dispose).toFloat(), (tabHeight / 2 + (bounds.bottom - bounds.top) / 2).toFloat(), p)

        }


        p.color = 0x222222
        p.strokeWidth = 2f
        for (i in tabs.indices) {
            val tabX = tabWidth * i
            canvas.drawLine((tabX - x_dispose + tabButtonWidth).toFloat(), 20f, (tabX - x_dispose + tabButtonWidth).toFloat(), (tabHeight - 20).toFloat(), p)
        }


        val selectedTabX = tabWidth * selectedScreenTabIndex!!
        p.color = MyColors.yellow
        canvas.drawRect((selectedTabX - x_dispose).toFloat(), 0f, (selectedTabX + tabWidth - x_dispose).toFloat(), 10f, p)
    }


    internal fun setBoundsOfThreeDots(): Rect {
        p.getTextBounds("...", 0, 3, bounds)
        return bounds
    }

    companion object {

        internal val p = Paint()
    }
}
