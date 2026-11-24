package com.adastra.focuswallpaper

import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
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
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TrashUiTest {
    private fun find(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) find(root.getChildAt(i), predicate)?.let { return it }
        return null
    }
    private fun tagged(root: View, tag: String) = find(root) { it.tag == tag }!!
    private fun measure(root: View) {
        repeat(3) {
            root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(740, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 360, 740)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
    private fun geometry(view: View): List<Int> {
        val point = IntArray(2).also { view.getLocationOnScreen(it) }
        return point.toList() + listOf(view.width, view.height)
    }
    private fun preview(root: View, name: String) {
        val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bitmap))
        val file = java.io.File("build/reports/ui/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun calendarAndChecklistTrashMatchAndCalendarRestoreKeepsChecklistTrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get(); val root = activity.window.decorView
        val store = Store(activity)
        store.saveTasks(listOf(Task(id = "task-trash", text = "삭제한 항목", deletedAt = 10)))
        val repo = NoteRepository(activity)
        val note = Note(id = "calendar-trash", title = "삭제한 항목", schedule = NoteSchedule(LocalDate.of(2026, 11, 24)), deletedAt = 10)
        repo.save(note)
        tagged(root, "nav:trash").performClick(); measure(root)
        val tags = listOf("trash:toolbar", "trash:card", "trash:row:0", "trash:title:0", "trash:detail:0", "trash:restore:0", "trash:delete:0")
        val original = tags.associateWith { geometry(tagged(root, it)) }
        val colors = tags.filter { tagged(root, it) is TextView }.associateWith { (tagged(root, it) as TextView).currentTextColor }
        preview(root, "trash-checklist")
        tagged(root, "trash:filter:calendar").performClick(); measure(root)
        tags.forEach { assertEquals(it, original[it], geometry(tagged(root, it))) }
        colors.forEach { (tag, color) -> assertEquals(color, (tagged(root, tag) as TextView).currentTextColor) }
        assertTrue((tagged(root, "trash:detail:0") as TextView).text.contains("11월 24일"))
        assertEquals(22, (tagged(root, "trash:restore:0") as TextView).compoundDrawables[0].bounds.width())
        preview(root, "trash-calendar")
        tagged(root, "trash:restore:0").performClick(); measure(root)
        assertEquals(0L, repo.get(note.id)!!.deletedAt)
        assertTrue(store.tasks().single().deletedAt > 0)
        val emptyCalendar = geometry(tagged(root, "trash:empty"))
        assertEquals("휴지통이 비어 있습니다", (tagged(root, "trash:empty") as TextView).text.toString())
        store.saveTasks(emptyList())
        tagged(root, "trash:filter:tasks").performClick(); measure(root)
        assertEquals(emptyCalendar, geometry(tagged(root, "trash:empty")))
        repo.close(); controller.pause().stop().destroy()
    }
    @Test fun calendarEmptyButtonRequiresConfirmationAndOnlyEmptiesCalendarTrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get(); val root = activity.window.decorView
        val store = Store(activity)
        store.saveTasks(listOf(Task(id = "task-trash", text = "할 일", deletedAt = 10)))
        val repo = NoteRepository(activity)
        val base = Note(title = "일정", schedule = NoteSchedule(LocalDate.of(2026, 11, 24)))
        val active = base.copy(id = "active"); val plain = base.copy(id = "plain", schedule = null, deletedAt = 10)
        listOf(active, plain, base.copy(id = "deleted-a", deletedAt = 10), base.copy(id = "deleted-b", deletedAt = 11)).forEach { repo.save(it) }
        tagged(root, "nav:trash").performClick()
        tagged(root, "trash:filter:calendar").performClick(); measure(root)
        fun empty() { find(root) { it is TextView && it.text.toString() == "휴지통 비우기" }!!.performClick() }
        empty()
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(2, repo.notes(includeTrash = true).count { it.deletedAt > 0 })
        empty()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(NoteUi(activity).muted, dialog.getButton(AlertDialog.BUTTON_POSITIVE).currentTextColor)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); measure(root)
        assertEquals(listOf(active.id), repo.notes(includeTrash = true).map { it.id })
        assertNotNull(repo.get(plain.id))
        assertTrue(store.tasks().single().deletedAt > 0)
        assertNotNull(tagged(root, "trash:empty"))
        repo.close(); controller.pause().stop().destroy()
    }
}
