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
    private fun measure(root: View, height: Int = 740) {
        repeat(3) {
            root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 360, height)
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
        assertTrue("month=${month.height}, scroll=${scroll.height}, top=${month.top}, display=${root.resources.displayMetrics}", month.height < scroll.height)
        assertTrue("monthBottom=${month.bottom}, scroll=${scroll.height}", month.bottom + 48 <= scroll.height)
        add.performClick()
        val intent = shadowOf(controller.get()).nextStartedActivity
        assertEquals(NoteEditorActivity::class.java.name, intent.component!!.className)
        controller.pause().stop().destroy()
    }
    @Test fun monthsFollowDragAndSnapToNeighbourWithoutOpeningDialog() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val root = controller.get().window.decorView
        find(root) { it.tag == "nav:calendar" }!!.performClick()
        measure(root)
        val month = find(root) { it is CalendarMonthView } as CalendarMonthView
        val before = month.shownMonth
        var movement = 0
        month.pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, offset: Float, pixels: Int) { movement = maxOf(movement, pixels) }
        })
        assertTrue(month.pager.beginFakeDrag())
        month.pager.fakeDragBy(-month.width * .7f)
        assertTrue("Content must move while the finger is down", movement > 0)
        month.pager.endFakeDrag()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        measure(root)
        assertEquals(before.plusMonths(1), month.shownMonth)
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        month.showMonth(before)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        measure(root)
        assertEquals(before, month.shownMonth)
        swipe(month, 100f, 108f, 200f, 70f)
        measure(root)
        assertEquals(before, month.shownMonth)
        controller.pause().stop().destroy()
    }
    @Test fun navigationIconsSurviveRepeatedTabSwitchAndCalendarResize() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val root = controller.get().window.decorView
        val ids = listOf("tasks", "calendar", "trash", "settings")
        val icons = ids.map { id -> find(root) { it.tag == "nav-icon:$id" }!! }
        repeat(3) {
            ids.forEach { id ->
                find(root) { it.tag == "nav:$id" }!!.performClick()
                measure(root, if (id == "calendar") 700 else 740)
                ids.forEachIndexed { i, name ->
                    val icon = find(root) { it.tag == "nav-icon:$name" } as android.widget.ImageView
                    assertSame(icons[i], icon)
                    assertNotNull(icon.drawable)
                    assertEquals(View.VISIBLE, icon.visibility)
                    assertEquals(24, icon.width); assertEquals(24, icon.height)
                }
            }
        }
        find(root) { it.tag == "nav:calendar" }!!.performClick()
        measure(root)
        val heading = find(root) { it is TextView && it.text.toString() == "캘린더" && it.parent != null && it.paddingBottom == 16 }!!
        assertEquals(16, heading.paddingBottom)
        controller.pause().stop().destroy()
    }
}
