package com.adastra.focuswallpaper

import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
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
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OtherSettingsTest {
    private fun find(view: View, predicate: (View) -> Boolean): View? {
        if(predicate(view)) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i),predicate)?.let { return it }
        return null
    }
    private fun tagged(view: View, tag: String) = find(view) { it.tag == tag }!!
    private fun flush() = shadowOf(Looper.getMainLooper()).idle()
    private fun measure(view: View) {
        repeat(3) {view.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(740,View.MeasureSpec.EXACTLY));view.layout(0,0,360,740);flush()}
    }
    private fun preview(view: View, name: String) {
        val scroll=find(view) { it is ScrollView } as ScrollView
        scroll.scrollTo(0,(scroll.getChildAt(0).height-scroll.height).coerceAtLeast(0));flush()
        val box=tagged(view,"settings:other-box")
        val boxPosition=IntArray(2).also { box.getLocationOnScreen(it) }
        val viewport=IntArray(2).also { scroll.getLocationOnScreen(it) }
        assertTrue("Other settings header is reachable",boxPosition[1] >= viewport[1])
        assertTrue("Other settings box is fully visible after scrolling",boxPosition[1]+box.height <= viewport[1]+scroll.height)
        val bitmap=android.graphics.Bitmap.createBitmap(360,740,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file=File("build/reports/ui/$name.png");file.parentFile!!.mkdirs();file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    @Test fun backupPrecedesCollapsibleOtherBoxAndSwitchPersistsAcrossScreensAndRestart() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get();val root=activity.window.decorView
        tagged(root,"nav:settings").performClick();measure(root)
        val details=tagged(root,"settings:other-content")
        assertEquals(View.GONE,details.visibility)
        val box=tagged(root,"settings:other-box")
        val backup=find(root) { it.contentDescription == "백업 파일로 내보내기" }!!
        assertTrue(IntArray(2).also { backup.getLocationOnScreen(it) }[1] < IntArray(2).also { box.getLocationOnScreen(it) }[1])
        preview(root,"settings-other-collapsed")
        tagged(root,"settings:other-toggle").performClick();measure(root)
        assertEquals(View.VISIBLE,details.visibility)
        val hide=tagged(root,"settings:hide-uncategorized") as Switch
        assertFalse(hide.isChecked);hide.performClick();assertTrue(Store(activity).hideCalendarUncategorized)
        val notification=find(details) { it is TextView && it.text.startsWith("앱 알림") }!!
        notification.performClick();assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS,shadowOf(activity).nextStartedActivity.action)
        preview(root,"settings-other-expanded")
        tagged(root,"nav:tasks").performClick();tagged(root,"nav:settings").performClick()
        assertTrue((tagged(root,"settings:hide-uncategorized") as Switch).isChecked)
        assertEquals(View.VISIBLE,tagged(root,"settings:other-content").visibility)
        tagged(root,"settings:other-toggle").performClick();assertEquals(View.GONE,tagged(root,"settings:other-content").visibility)
        controller.pause().stop().destroy()
        val reopened=Robolectric.buildActivity(MainActivity::class.java).setup();tagged(reopened.get().window.decorView,"nav:settings").performClick()
        assertTrue((tagged(reopened.get().window.decorView,"settings:hide-uncategorized") as Switch).isChecked)
        reopened.pause().stop().destroy()
    }
    @Test fun hiddenCategoryLeavesAllEntriesAndDefaultCategoryIntactAndClearsStaleFilter() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get();val repo=NoteRepository(activity)
        val note=Note(id="uncat",title="미분류 메모",schedule=NoteSchedule(LocalDate.now()))
        val trash=note.copy(id="deleted",deletedAt=123)
        repo.save(note);repo.save(trash);val category=repo.categories().single()
        val store=Store(activity);store.hideCalendarUncategorized=true
        val ui=CalendarUi(activity,repo) {};ui.filter=NOTE_UNCATEGORIZED
        val parent=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL };ui.render(parent,500)
        assertEquals("",ui.filter)
        assertNull(find(parent) { it.tag == "calendar:category:$NOTE_UNCATEGORIZED" })
        assertNotNull(find(parent) { it.tag == "calendar:category:" })
        ui.filter=NOTE_UNCATEGORIZED;ui.showDay(LocalDate.now());flush()
        val dialog=ShadowAlertDialog.getLatestAlertDialog();val body=dialog.window!!.decorView
        assertEquals("",ui.filter)
        assertNull(find(body) { it.tag == "calendar-day:category:$NOTE_UNCATEGORIZED" })
        assertNotNull(find(body) { it is TextView && it.text == note.title })
        assertEquals(note,repo.get(note.id));assertEquals(trash,repo.get(trash.id));assertEquals(category,repo.categories().single())
        store.hideCalendarUncategorized=false;val shown=LinearLayout(activity);ui.render(shown,500)
        assertNotNull(find(shown) { it.tag == "calendar:category:$NOTE_UNCATEGORIZED" })
        ui.dispose();repo.close();controller.pause().stop().destroy()
    }
    @Test fun orderingSkipsHiddenCategoryAndRetainsItsStoredPosition() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get();val repo=NoteRepository(activity)
        repo.saveCategory(NoteCategory("a","공부",123));repo.saveCategory(NoteCategory("b","약속",456));repo.moveCategory("a",-1)
        assertEquals(listOf("a",NOTE_UNCATEGORIZED,"b"),repo.categories().map { it.id })
        Store(activity).hideCalendarUncategorized=true
        val root=activity.window.decorView;tagged(root,"nav:calendar").performClick();measure(root)
        tagged(root,"calendar:category:a").performLongClick();flush()
        val dialog=ShadowAlertDialog.getLatestAlertDialog();val body=dialog.window!!.decorView
        assertEquals("약속",(tagged(body,"category-order:right-name") as TextView).text.toString())
        assertEquals(View.INVISIBLE,find(body) { it.contentDescription == "공부 분류를 한 칸 왼쪽으로 이동" }!!.visibility)
        find(body) { it.contentDescription == "공부 분류를 한 칸 오른쪽으로 이동" }!!.performClick();flush()
        assertEquals(listOf("b",NOTE_UNCATEGORIZED,"a"),repo.categories().map { it.id })
        dialog.dismiss();flush();repo.close();controller.pause().stop().destroy()
    }
    @Test fun backupRestoresNewOptionAndLegacyBackupsDefaultToShowingUncategorized() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup();val context=controller.get();val store=Store(context)
        store.hideCalendarUncategorized=true;store.completedTasksLast=false
        val tasks=listOf(Task("a","A"),Task("b","B",done=true,completedAt=123),Task("c","C"))
        store.saveTasks(tasks)
        val file=File.createTempFile("misc-backup-",".db",context.cacheDir)
        BackupDatabase.write(context,file,BackupSnapshot.capture(context))
        val snapshot=BackupImporter.read(context,file)
        assertEquals(true,snapshot.settings["calendar_hide_uncategorized"])
        store.hideCalendarUncategorized=false;store.completedTasksLast=true;store.saveTasks(emptyList())
        BackupImporter.restore(context,snapshot);assertTrue(store.hideCalendarUncategorized)
        assertFalse(store.completedTasksLast);assertEquals(tasks,store.tasks())
        assertEquals(listOf("a","b","c"),TaskOrder.visible(store.tasks(),DEFAULT_CATEGORY,store.completedTasksLast).map { it.id })
        val db=SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READWRITE)
        db.execSQL("ALTER TABLE app_settings RENAME TO extended_settings")
        db.execSQL("CREATE TABLE app_settings AS SELECT id,default_focus_mode,font_step,last_minutes,last_stopwatch,wallpaper_enabled,wallpaper_color_argb FROM extended_settings")
        db.execSQL("DROP TABLE extended_settings");db.close()
        val legacy=BackupImporter.read(context,file);assertEquals(false,legacy.settings["calendar_hide_uncategorized"])
        BackupImporter.restore(context,legacy);assertFalse(store.hideCalendarUncategorized);assertTrue(store.completedTasksLast)
        SQLiteDatabase.deleteDatabase(file);controller.pause().stop().destroy()
    }
}
