package com.adastra.focuswallpaper

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

object CalendarLayout {
    fun rowHeight(viewportDp: Int): Int = ((viewportDp - 104) / 6).coerceIn(48, 72)
    fun swipeDirection(dx: Float, dy: Float, width: Int, density: Float): Int {
        val threshold = maxOf(48f * density, width * .16f)
        if (abs(dx) < threshold || abs(dx) < abs(dy) * 1.4f) return 0
        return if (dx < 0) 1 else -1
    }
    fun dateInMonth(date: LocalDate, month: YearMonth): LocalDate = month.atDay(minOf(date.dayOfMonth, month.lengthOfMonth()))
}

/** Intercept horizontal drags before date/note clicks; leave vertical scrolls to the page. */
class CalendarMonthView(context: Context, private val changeMonth: (Int) -> Unit) : LinearLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var cancelled = false
    init { orientation = VERTICAL }
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY; dragging = false; cancelled = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> { cancelled = true; dragging = false; parent?.requestDisallowInterceptTouchEvent(false) }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX; val dy = event.rawY - downY
                if (!cancelled && abs(dx) > slop && abs(dx) > abs(dy) * 1.4f) {
                    dragging = true; parent?.requestDisallowInterceptTouchEvent(true); return true
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> { dragging = false }
        }
        return dragging
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) return true
        if (!dragging && !cancelled && event.actionMasked == MotionEvent.ACTION_MOVE) {
            val dx = event.rawX - downX; val dy = event.rawY - downY
            if (abs(dx) > slop && abs(dx) > abs(dy) * 1.4f) {
                dragging = true; parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
        if (!dragging) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> { /* The gesture stays on the month surface, without moving the page. */ }
            MotionEvent.ACTION_POINTER_DOWN -> cancelled = true
            MotionEvent.ACTION_UP -> {
                val direction = if (cancelled) 0 else CalendarLayout.swipeDirection(event.rawX - downX, event.rawY - downY, width, resources.displayMetrics.density)
                dragging = false; parent?.requestDisallowInterceptTouchEvent(false)
                if (direction != 0) changeMonth(direction)
            }
            MotionEvent.ACTION_CANCEL -> { dragging = false; parent?.requestDisallowInterceptTouchEvent(false) }
        }
        return true
    }
}
