package com.adastra.focuswallpaper

import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h740dp-mdpi")
class CalendarInteractionTest {
    private fun find(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) find(root.getChildAt(i), predicate)?.let { return it }
        return null
    }
    private fun measure(root: View) {
        repeat(3) {
            root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(740, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 360, 740)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
    private fun swipe(view: View, fromX: Float, toX: Float, fromY: Float, toY: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP).forEachIndexed { i, action ->
            val event = MotionEvent.obtain(now, now + i * 30, action, if (i == 0) fromX else toX, if (i == 0) fromY else toY, 0)
            view.dispatchTouchEvent(event); event.recycle()
        }
    }
    @Test fun floatingAddIsSeparateFromScrollableContentAndCalendarFits() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val root = controller.get().window.decorView
        find(root) { it is TextView && it.text.toString() == "캘린더" }!!.performClick()
        measure(root)
        val add = find(root) { it.contentDescription == "일정 추가" }!!
        val month = find(root) { it is CalendarMonthView }!!
        val scroll = month.parent.parent as ScrollView
        assertTrue(add.parent is FrameLayout)
        assertSame(scroll.parent, add.parent)
        assertEquals(56, add.width); assertEquals(add.width, add.height)
        assertTrue(month.height < scroll.height)
        assertTrue(month.bottom + 48 <= scroll.height)
        add.performClick()
        val intent = shadowOf(controller.get()).nextStartedActivity
        assertEquals(NoteEditorActivity::class.java.name, intent.component!!.className)
        controller.pause().stop().destroy()
    }
    @Test fun horizontalSwipeChangesMonthWithoutOpeningDateDialogAndVerticalSwipeDoesNot() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val root = controller.get().window.decorView
        find(root) { it is TextView && it.text.toString() == "캘린더" }!!.performClick()
        measure(root)
        val before = YearMonth.now()
        var month = find(root) { it is CalendarMonthView }!!
        swipe(month, 270f, 50f, 160f, 165f)
        measure(root)
        val next = before.plusMonths(1)
        assertNotNull(find(root) { it is TextView && it.text.toString() == "${next.year}. ${next.monthValue}" })
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        month = find(root) { it is CalendarMonthView }!!
        swipe(month, 50f, 270f, 160f, 165f)
        measure(root)
        assertNotNull(find(root) { it is TextView && it.text.toString() == "${before.year}. ${before.monthValue}" })
        month = find(root) { it is CalendarMonthView }!!
        swipe(month, 100f, 108f, 200f, 70f)
        measure(root)
        assertNotNull(find(root) { it is TextView && it.text.toString() == "${before.year}. ${before.monthValue}" })
        controller.pause().stop().destroy()
    }
}
