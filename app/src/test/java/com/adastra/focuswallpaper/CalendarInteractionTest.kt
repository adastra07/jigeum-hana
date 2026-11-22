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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
        var ancestor = month.parent
        while (ancestor !is ScrollView) ancestor = ancestor.parent
        val scroll = ancestor
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
        val header = find(root) { it.tag == "calendar:header" }!!
        val title = find(root) { it.tag == "calendar:month-label" } as TextView
        val today = find(root) { it is TextView && it.text.toString() == "오늘" }!!
        val search = find(root) { it.contentDescription == "캘린더 메모 검색" }!!
        val fixed = listOf(header, title, today, search).associateWith { view ->
            IntArray(2).also { view.getLocationOnScreen(it) }.toList()
        }
        assertNull(find(month) { it.tag == "calendar:header" })
        var movement = 0
        month.pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, offset: Float, pixels: Int) { movement = maxOf(movement, pixels) }
        })
        assertTrue(month.pager.beginFakeDrag())
        month.pager.fakeDragBy(-month.width * .7f)
        assertTrue("Content must move while the finger is down", movement > 0)
        fixed.forEach { (view, location) ->
            assertEquals(location, IntArray(2).also { view.getLocationOnScreen(it) }.toList())
            assertEquals(0f, view.translationX, 0f)
        }
        month.pager.endFakeDrag()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        measure(root)
        assertEquals(before.plusMonths(1), month.shownMonth)
        assertEquals("${month.shownMonth.year}. ${month.shownMonth.monthValue}", title.text.toString())
        assertSame(header, find(root) { it.tag == "calendar:header" })
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
    @Test fun categoryGeometryMatchesAndSelectionKeepsManualScrollPosition() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val store = Store(activity)
        store.saveCategories(store.categories() + (0..14).map { TaskCategory("task$it", "목록$it") })
        NoteRepository(activity).use { repo ->
            (0..14).forEach { repo.saveCategory(NoteCategory("calendar$it", "목록$it", 0xffdddddd.toInt())) }
        }
        val root = activity.window.decorView
        find(root) { it.tag == "nav:settings" }!!.performClick()
        find(root) { it.tag == "nav:tasks" }!!.performClick()
        measure(root)
        val tasksStrip = find(root) { it.tag == "tasks:categories" }!!
        val taskCard = find(root) { it.tag == "tasks:main-card" }!!
        val taskTop = IntArray(2).also { taskCard.getLocationOnScreen(it) }[1]
        val stripTop = IntArray(2).also { tasksStrip.getLocationOnScreen(it) }[1]
        val taskButton = find(root) { it.tag == "tasks:category:task0" }!!
        val buttonHeight = taskButton.height
        val buttonWidth = taskButton.width
        fun selectVisible(key: String) {
            var scroll = find(root) { it.tag == "$key:category-scroll" } as android.widget.HorizontalScrollView
            scroll.scrollTo(240, 0)
            val offset = scroll.scrollX
            assertTrue(offset > 0)
            val content = scroll.getChildAt(0) as ViewGroup
            val target = (0 until content.childCount).map { content.getChildAt(it) }
                .first { !it.isSelected && it.left >= offset && it.right <= offset + scroll.width }
            val tag = target.tag
            target.performClick()
            measure(root)
            scroll = find(root) { it.tag == "$key:category-scroll" } as android.widget.HorizontalScrollView
            assertEquals(offset, scroll.scrollX)
            val selected = find(root) { it.tag == tag }!!
            selected.performClick()
            measure(root)
            assertSame(selected, find(root) { it.tag == tag })
            assertEquals(offset, scroll.scrollX)
        }
        selectVisible("tasks")
        find(root) { it.tag == "nav:calendar" }!!.performClick()
        measure(root)
        val calendarStrip = find(root) { it.tag == "calendar:categories" }!!
        val calendarCard = find(root) { it.tag == "calendar:main-card" }!!
        assertEquals(tasksStrip.height, calendarStrip.height)
        assertEquals(stripTop, IntArray(2).also { calendarStrip.getLocationOnScreen(it) }[1])
        assertEquals(taskTop, IntArray(2).also { calendarCard.getLocationOnScreen(it) }[1])
        val calendarButton = find(root) { it.tag == "calendar:category:calendar0" }!!
        assertEquals(buttonHeight, calendarButton.height)
        assertEquals(buttonWidth, calendarButton.width)
        selectVisible("calendar")
        controller.pause().stop().destroy()
    }

}
