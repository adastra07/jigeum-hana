package com.adastra.focuswallpaper

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NoteRepositoryTest {
    private lateinit var context: Context
    private lateinit var repo: NoteRepository
    private val day = LocalDate.of(2026, 11, 25)
    @Before fun createDatabase() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("notes.db")
        repo = NoteRepository(context)
    }
    @After fun closeDatabase() { repo.close(); context.deleteDatabase("notes.db") }
    @Test fun notesAndCategoryOrderSurviveReopeningAndColorChangesAreShared() {
        val category = NoteCategory("school", "공부", NoteUi.colors[0])
        repo.saveCategory(category)
        val note = Note(title = "수학", categoryId = category.id, schedule = NoteSchedule(day))
        repo.save(note); repo.moveCategory(category.id, -1)
        repo.saveCategory(category.copy(color = NoteUi.colors[1]))
        repo.close(); repo = NoteRepository(context)
        assertEquals(note, repo.get(note.id))
        assertEquals(category.id, repo.categories().first().id)
        assertEquals(NoteUi.colors[1], repo.categories().first().color)
    }
    @Test fun deletingCategoryMovesActiveAndTrashedNotesWithoutLosingTheirData() {
        val category = NoteCategory("school", "공부", NoteUi.colors[0]); repo.saveCategory(category)
        val active = Note(title = "수학", body = "범위", categoryId = category.id, schedule = NoteSchedule(day))
        val trash = active.copy(id = "trash").trash(10)
        repo.save(active); repo.save(trash); repo.removeCategory(category.id)
        assertEquals(active.copy(categoryId = NOTE_UNCATEGORIZED), repo.get(active.id))
        assertEquals(trash.copy(categoryId = NOTE_UNCATEGORIZED), repo.get(trash.id))
        assertEquals(listOf(NOTE_UNCATEGORIZED), repo.categories().map { it.id })
    }
    @Test fun calendarTrashNeverDeletesActiveNotesOrdinaryNotesOrChecklistItems() {
        val active = Note(title = "활성", schedule = NoteSchedule(day))
        val deleted = active.copy(id = "deleted").trash(10)
        val ordinary = Note(title = "일반 메모").trash(10)
        val task = Task(text = "할 일").trash(10)
        Store(context).saveTasks(listOf(task))
        listOf(active, deleted, ordinary).forEach { repo.save(it) }
        repo.permanentlyDelete(active.id)
        assertEquals(active, repo.get(active.id))
        repo.emptyCalendarTrash()
        assertNull(repo.get(deleted.id)); assertEquals(active, repo.get(active.id))
        assertEquals(ordinary, repo.get(ordinary.id)); assertEquals(listOf(task), Store(context).tasks())
    }
    private fun input(root: View, description: String): EditText? {
        if (root is EditText && root.contentDescription == description) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) input(root.getChildAt(i), description)?.let { return it }
        return null
    }
    @Test fun editorDoesNotCreateBlankNotesAndAutosavesTextAcrossRecreation() {
        val intent = Intent(context, NoteEditorActivity::class.java).putExtra("date", day.toString())
        val controller = Robolectric.buildActivity(NoteEditorActivity::class.java, intent).setup()
        assertTrue(repo.notes().isEmpty())
        val activity = controller.get()
        input(activity.window.decorView, "메모 제목")!!.setText("시험")
        input(activity.window.decorView, "메모 내용")!!.setText("준비물\n계산기")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
        val note = repo.notes().single()
        assertEquals("시험", note.title); assertEquals("준비물\n계산기", note.body)
        controller.recreate()
        assertEquals("시험", input(controller.get().window.decorView, "메모 제목")!!.text.toString())
        assertEquals(note, repo.get(note.id))
        controller.pause().stop().destroy()
    }
    @Test fun leavingEditorFlushesPendingTextWithoutWaitingForDebounce() {
        val intent = Intent(context, NoteEditorActivity::class.java).putExtra("date", day.toString())
        val controller = Robolectric.buildActivity(NoteEditorActivity::class.java, intent).setup()
        input(controller.get().window.decorView, "메모 제목")!!.setText("빠른 입력")
        controller.pause()
        assertEquals("빠른 입력", repo.notes().single().title)
        controller.stop().destroy()
    }
    @Test fun calendarTabAndDayDialogOpenWithExistingChecklistScreen() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        fun find(root: View, predicate: (View) -> Boolean): View? {
            if (predicate(root)) return root
            if (root is ViewGroup) for (i in 0 until root.childCount) find(root.getChildAt(i), predicate)?.let { return it }
            return null
        }
        val root = controller.get().window.decorView
        val tab = find(root) { it is android.widget.TextView && it.text.toString() == "캘린더" }
        assertNotNull(tab); tab!!.performClick()
        assertNotNull(find(root) { it is android.widget.TextView && it.text.toString() == "오늘" })
        val today = LocalDate.now()
        val label = "${today.year}년 ${NoteUi(controller.get()).date(today)}"
        val cell = find(root) { it.contentDescription?.toString() == label }
        assertNotNull(cell); cell!!.performClick()
        assertNotNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog())
        controller.pause().stop().destroy()
    }

}
