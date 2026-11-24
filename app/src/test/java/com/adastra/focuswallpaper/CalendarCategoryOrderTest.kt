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
class CalendarCategoryOrderTest {
    private fun find(view: View, predicate: (View) -> Boolean): View? {
        if (predicate(view)) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i), predicate)?.let { return it }
        return null
    }
    private fun tagged(root: View, tag: String) = find(root) { it.tag == tag }!!
    private fun named(root: View, name: String) = find(root) { it.contentDescription == name }!!
    private fun flush() = shadowOf(Looper.getMainLooper()).idle()
    private fun measure(root: View) {
        repeat(3) { root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(740, View.MeasureSpec.EXACTLY));root.layout(0,0,360,740);flush() }
    }
    @Test fun calendarKeepsSelectedCategoryCentredAcrossRepeatedMovesAndPreservesData() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup(); val activity = controller.get()
        val repo = NoteRepository(activity)
        val a = NoteCategory("a", "공부", 123); val b = NoteCategory("b", "약속", 456)
        repo.saveCategory(a);repo.saveCategory(b)
        val note = Note(id="note", title="보존", categoryId=a.id, schedule=NoteSchedule(LocalDate.now()))
        val trash = note.copy(id="trash",deletedAt=123)
        repo.save(note);repo.save(trash)
        val root=activity.window.decorView
        tagged(root,"nav:calendar").performClick();measure(root)
        tagged(root,"calendar:category:a").performLongClick();flush()
        val dialog=ShadowAlertDialog.getLatestAlertDialog();val body=dialog.window!!.decorView
        assertTrue(dialog.isShowing)
        assertNull(find(body) { it is TextView && it.text in listOf("왼쪽으로","오른쪽으로") })
        assertEquals("공부",(tagged(body,"category-order:current-name") as TextView).text.toString())
        assertEquals("미분류",(tagged(body,"category-order:left-name") as TextView).text.toString())
        assertEquals("약속",(tagged(body,"category-order:right-name") as TextView).text.toString())
        named(body,"공부 분류를 한 칸 오른쪽으로 이동").performClick();flush()
        assertTrue(dialog.isShowing);assertEquals(listOf(NOTE_UNCATEGORIZED,"b","a"),repo.categories().map { it.id })
        assertEquals(View.INVISIBLE,named(body,"공부 분류를 한 칸 오른쪽으로 이동").visibility)
        repeat(2) { named(body,"공부 분류를 한 칸 왼쪽으로 이동").performClick();flush() }
        assertEquals(listOf("a",NOTE_UNCATEGORIZED,"b"),repo.categories().map { it.id })
        assertEquals("공부",(tagged(body,"category-order:current-name") as TextView).text.toString())
        assertEquals(View.INVISIBLE,named(body,"공부 분류를 한 칸 왼쪽으로 이동").visibility)
        assertEquals(a,repo.categories().first());assertEquals(note,repo.get(note.id));assertEquals(trash,repo.get(trash.id))
        dialog.dismiss();flush();repo.close()
        val reopened=NoteRepository(activity);assertEquals("a",reopened.categories().first().id);reopened.close()
        controller.pause().stop().destroy()
    }
    @Test fun uncategorizedCanMoveAndDayDialogUsesSameControls() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get();val repo=NoteRepository(activity)
        repo.saveCategory(NoteCategory("a","분류",123))
        val ui=CalendarUi(activity,repo) {};ui.showDay(LocalDate.now());flush()
        val day=ShadowAlertDialog.getLatestAlertDialog()
        tagged(day.window!!.decorView,"calendar-day:category:$NOTE_UNCATEGORIZED").performLongClick();flush()
        val menu=ShadowAlertDialog.getLatestAlertDialog();val body=menu.window!!.decorView
        assertNotNull(find(body) { it is CategoryOrderControls })
        assertNull(find(body) { it is TextView && it.text == "분류 삭제" })
        named(body,"미분류 분류를 한 칸 오른쪽으로 이동").performClick();flush()
        assertEquals(listOf("a",NOTE_UNCATEGORIZED),repo.categories().map { it.id });assertTrue(menu.isShowing)
        val rename=find(body) { it is TextView && it.text == "이름·색상 변경" }!!
        rename.performClick();flush()
        assertEquals("캘린더 분류 편집",shadowOf(ShadowAlertDialog.getLatestAlertDialog()).title.toString())
        ShadowAlertDialog.getLatestAlertDialog().dismiss();flush();ui.dispose();repo.close();controller.pause().stop().destroy()
    }
    @Test fun checklistAndCalendarOrderControlsHaveMatchingGeometryAndChecklistStillMoves() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        val store=Store(activity);store.saveCategories(listOf(TaskCategory(DEFAULT_CATEGORY,"할 일"),TaskCategory("a","공부"),TaskCategory("b","약속")))
        val repo=NoteRepository(activity);repo.saveCategory(NoteCategory("a","공부",123));repo.saveCategory(NoteCategory("b","약속",456))
        val root=activity.window.decorView
        tagged(root,"nav:settings").performClick();tagged(root,"nav:tasks").performClick();measure(root)
        tagged(root,"tasks:category:a").performLongClick();flush()
        var dialog=ShadowAlertDialog.getLatestAlertDialog()
        var controls=find(dialog.window!!.decorView) { it is CategoryOrderControls } as CategoryOrderControls
        measure(dialog.window!!.decorView)
        val sizes=(0 until controls.childCount).map { controls.getChildAt(it).let { v->listOf(v.width,v.height,(v as TextView).textSize.toInt(),v.paddingLeft,v.paddingTop) } }
        named(dialog.window!!.decorView,"공부 목록을 한 칸 오른쪽으로 이동").performClick();flush()
        assertEquals("a",store.categories().last().id)
        dialog.dismiss();flush();tagged(root,"nav:calendar").performClick();measure(root)
        tagged(root,"calendar:category:a").performLongClick();flush()
        dialog=ShadowAlertDialog.getLatestAlertDialog();measure(dialog.window!!.decorView)
        controls=find(dialog.window!!.decorView) { it is CategoryOrderControls } as CategoryOrderControls
        assertEquals(sizes,(0 until controls.childCount).map { controls.getChildAt(it).let { v->listOf(v.width,v.height,(v as TextView).textSize.toInt(),v.paddingLeft,v.paddingTop) } })
        val bitmap=android.graphics.Bitmap.createBitmap(360,740,android.graphics.Bitmap.Config.ARGB_8888)
        dialog.window!!.decorView.draw(android.graphics.Canvas(bitmap))
        val file=java.io.File("build/reports/ui/calendar-category-order.png");file.parentFile!!.mkdirs();file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        dialog.dismiss();flush();repo.close();controller.pause().stop().destroy()
    }
}
