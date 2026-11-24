package com.adastra.focuswallpaper

import android.app.Activity
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupImporterTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun backup(snapshot: BackupSnapshot = BackupSnapshot.capture(context)): File =
        File.createTempFile("import-test-", ".db", context.cacheDir).also { BackupDatabase.write(context, it, snapshot) }
    private fun find(view: View, description: String): View? {
        if (view.contentDescription == description || view.tag == description) return view
        if (view is ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i), description)?.let { return it }
        return null
    }
    @Test fun exportedDataRoundTripsIncludingOrderTrashSettingsAndPausedStopwatch() {
        val store = Store(context)
        store.saveCategories(listOf(TaskCategory("other", "먼저"), TaskCategory(DEFAULT_CATEGORY, "나중")))
        store.saveTasks(listOf(Task("watch", "시간", stopwatch = true, stopwatchMs = 123456), Task("trash", "삭제", categoryId = "other", deletedAt = 12)))
        store.presets = listOf(0, 20, 180); store.fontStep = 8; store.wallpaperEnabled = false
        store.selected = "watch"; store.category = "other"
        store.timer = TimerState(status = "paused", taskId = "watch", title = "시간", mode = "stopwatch", elapsedMs = 123456)
        val repo = NoteRepository(context)
        repo.saveCategory(NoteCategory("new", "분류", 123))
        val note = Note("note", "제목", "본문\n'한글'", "new", NoteSchedule(LocalDate.of(2026,11,24)), deletedAt = 123)
        repo.save(note)
        val file = backup(); val snapshot = BackupImporter.read(context, file)
        store.saveTasks(listOf(Task(text = "임시"))); store.presets = listOf(1,2,3); store.fontStep = 0
        repo.save(Note(id = "replace", title = "교체", schedule = NoteSchedule(LocalDate.now())))
        repo.save(Note(id = "ordinary", title = "일반 노트"))
        BackupImporter.restore(context, snapshot)
        assertEquals(snapshot.tasks, store.tasks()); assertEquals(snapshot.taskCategories, store.categories())
        assertEquals(snapshot.presets, store.presets); assertEquals(8, store.fontStep); assertFalse(store.wallpaperEnabled)
        assertEquals(note, repo.get("note")); assertNull(repo.get("replace")); assertNotNull(repo.get("ordinary"))
        assertEquals(snapshot.calendarCategories, repo.categories())
        assertEquals("paused", store.timer.status); assertEquals(123456L, store.timer.elapsedMs); assertEquals(0L, store.timer.startedAt)
        assertEquals("watch", store.selected); assertEquals("other", store.category)
        assertFalse(File(context.filesDir,"import-undo.json").exists())
        repo.close(); SQLiteDatabase.deleteDatabase(file)
    }
    @Test fun invalidFilesAreRejectedBeforeAnyDataChanges() {
        val store = Store(context); store.saveTasks(listOf(Task(text = "유지")))
        val before = context.getSharedPreferences("focus",0).all.toMap()
        val file = backup()
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        db.execSQL("PRAGMA user_version=99"); db.close()
        assertThrows(IllegalArgumentException::class.java) { BackupImporter.read(context, file) }
        file.writeText("not a database")
        assertThrows(Exception::class.java) { BackupImporter.read(context, file) }
        assertEquals(before, context.getSharedPreferences("focus",0).all)
        SQLiteDatabase.deleteDatabase(file)
    }
    @Test fun weakenedInputSchemaCannotBypassValueAndReferenceChecks() {
        val file = backup()
        val db = SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READWRITE)
        db.execSQL("PRAGMA ignore_check_constraints=ON")
        db.execSQL("UPDATE app_settings SET font_step=99")
        db.close()
        assertThrows(Exception::class.java) { BackupImporter.read(context,file) }
        SQLiteDatabase.deleteDatabase(file)
    }
    @Test fun invalidDatesAndMissingDefaultCategoryAreRejected() {
        val store = Store(context); store.saveCategories(listOf(TaskCategory("other","분류"),TaskCategory(DEFAULT_CATEGORY,"기본")))
        val file = backup()
        val db = SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READWRITE)
        db.execSQL("UPDATE focus_state SET selected_category_id='other'")
        db.execSQL("DELETE FROM task_categories WHERE id='default'"); db.close()
        assertThrows(IllegalArgumentException::class.java) { BackupImporter.read(context,file) }
        SQLiteDatabase.deleteDatabase(file)
        val next = backup(); val editor = SQLiteDatabase.openDatabase(next.path,null,SQLiteDatabase.OPEN_READWRITE)
        editor.execSQL("INSERT INTO calendar_entries VALUES ('bad','','','uncategorized','2026-99-99','2026-99-99',NULL,NULL,0,0,0,'plain')");editor.close()
        assertThrows(Exception::class.java) { BackupImporter.read(context,next) }
        SQLiteDatabase.deleteDatabase(next)
    }
    @Test fun writeFailureRollsBackCalendarAndPreferences() {
        val repo = NoteRepository(context)
        val ordinary = Note(id="conflict",title="보존")
        repo.save(ordinary)
        val existing = Note(id="calendar",title="유지",schedule=NoteSchedule(LocalDate.now()))
        repo.save(existing)
        val snapshot = BackupSnapshot.capture(context).copy(entries=listOf(ordinary.copy(schedule=NoteSchedule(LocalDate.now()))))
        val before = context.getSharedPreferences("focus",0).all.toMap()
        assertThrows(IllegalArgumentException::class.java) { BackupImporter.restore(context,snapshot) }
        assertEquals(ordinary,repo.get("conflict")); assertEquals(existing,repo.get("calendar"))
        assertEquals(before,context.getSharedPreferences("focus",0).all)
        repo.close()
    }
    @Test fun interruptedPreferenceCommitRecoversAndCompletedCommitIsKept() {
        val prefs = context.getSharedPreferences("focus",0)
        prefs.edit().putString("selected","partially-imported").commit()
        val undo = JSONObject().put("selected",JSONObject().put("type","string").put("value","old"))
            .put("remaining",JSONObject().put("type","long").put("value",123L))
        val file = File(context.filesDir,"import-undo.json");file.writeText(undo.toString())
        BackupImporter.recover(context)
        assertEquals("old",prefs.getString("selected",null));assertEquals(123L,prefs.getLong("remaining",0)); assertFalse(file.exists())
        val repo = NoteRepository(context);repo.writableDatabase.execSQL("CREATE TABLE IF NOT EXISTS import_commits(token TEXT PRIMARY KEY)")
        repo.writableDatabase.execSQL("INSERT INTO import_commits VALUES ('pending')")
        prefs.edit().putString("selected","completed").commit();file.writeText(undo.toString())
        BackupImporter.recover(context)
        assertEquals("completed",prefs.getString("selected",null));assertFalse(file.exists())
        repo.readableDatabase.rawQuery("SELECT * FROM import_commits",null).use { assertFalse(it.moveToFirst()) };repo.close()
    }
    @Test fun pickerAndConfirmationCancellationKeepLiveDataAndButtonsShareCompactRow() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup();val activity=controller.get()
        find(activity.window.decorView,"nav:settings")!!.performClick()
        val export = find(activity.window.decorView,"백업 파일로 내보내기")!!
        val importButton = find(activity.window.decorView,"백업 파일 가져오기")!!
        assertSame(export.parent,importButton.parent)
        assertEquals(export.minimumHeight,importButton.minimumHeight)
        importButton.performClick()
        val picker = shadowOf(activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_OPEN_DOCUMENT,picker.intent.action);assertEquals("*/*",picker.intent.type)
        val before=context.getSharedPreferences("focus",0).all.toMap()
        shadowOf(activity).receiveResult(picker.intent,Activity.RESULT_CANCELED,null)
        assertEquals(before,context.getSharedPreferences("focus",0).all)
        val file=backup();val uri=Uri.parse("content://test.documents/import.db")
        shadowOf(activity.contentResolver).registerInputStream(uri,file.inputStream())
        importButton.performClick();val next=shadowOf(activity).nextStartedActivityForResult
        shadowOf(activity).receiveResult(next.intent,Activity.RESULT_OK,Intent().setData(uri))
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(ShadowAlertDialog.getLatestAlertDialog()?.isShowing != true && System.nanoTime()<deadline) {
            Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle()
        }
        val dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertTrue(dialog.isShowing)
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(BackupImporter.isBusy());assertEquals(before,context.getSharedPreferences("focus",0).all)
        SQLiteDatabase.deleteDatabase(file);controller.pause().stop().destroy()
    }
}
