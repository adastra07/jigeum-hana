package com.adastra.focuswallpaper

import android.app.Activity
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupDatabaseTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun target() = File.createTempFile("test-backup-", ".db", context.cacheDir)
    private fun <T> read(file: File, block: (SQLiteDatabase) -> T): T {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        try { return block(db) } finally { db.close() }
    }
    private fun scalar(db: SQLiteDatabase, sql: String): String = db.rawQuery(sql, null).use { assertTrue(it.moveToFirst()); it.getString(0) }
    private fun find(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) find(root.getChildAt(i), predicate)?.let { return it }
        return null
    }
    @Test fun standaloneFilePreservesRecordsOrderTrashSettingsAndScheduleWithoutChangingSource() {
        val store = Store(context)
        store.saveCategories(listOf(TaskCategory("second", "먼저"), TaskCategory(DEFAULT_CATEGORY, "나중")))
        store.saveTasks(listOf(
            Task(id = "trash", text = "삭제", done = true, minutes = 0, categoryId = "second", deletedAt = 123),
            Task(id = "watch", text = "기록", minutes = 30, stopwatch = true, stopwatchMs = 6789),
            Task(id = "timer", text = "한글 ' \" 줄\n둘", minutes = 45)))
        store.presets = listOf(0, 30, 90); store.fontStep = 8; store.lastMinutes = 45
        store.defaultFocusMode = FocusDefaults.UNASSIGNED; store.lastStopwatch = true
        store.wallpaperEnabled = false; store.wallpaperColor = 0xffabc123.toInt()
        store.category = "second"; store.selected = "watch"
        val repo = NoteRepository(context)
        val category = NoteCategory("custom", "일정", 0xffaabbcc.toInt())
        repo.saveCategory(category); repo.moveCategory(category.id, -1)
        val scheduled = Note(id = "dated", title = "기간", body = "본문\n'따옴표'", categoryId = category.id,
            schedule = NoteSchedule(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 26), 600, 570), deletedAt = 456, createdAt = 100, updatedAt = 200)
        repo.save(scheduled)
        repo.save(Note(id = "all-day", title = "종일", schedule = NoteSchedule(LocalDate.of(2026, 11, 24))))
        repo.save(Note(id = "future-note", title = "아직 범위 밖"))
        val preferencesBefore = context.getSharedPreferences("focus", 0).all.toMap()
        val snapshot = BackupSnapshot.capture(context)
        val file = target(); BackupDatabase.write(context, file, snapshot)
        val isolated = File(context.cacheDir, "standalone-copy.db"); file.copyTo(isolated, overwrite = true)
        read(isolated) { db ->
            assertEquals(1, db.version)
            assertEquals(BackupDatabase.APPLICATION_ID.toString(), scalar(db, "PRAGMA application_id"))
            assertEquals("jigeum-hana", scalar(db, "SELECT format FROM backup_info"))
            assertEquals("second,default", scalar(db, "SELECT group_concat(id,',') FROM (SELECT id FROM task_categories ORDER BY position)"))
            assertEquals("trash,watch,timer", scalar(db, "SELECT group_concat(id,',') FROM (SELECT id FROM tasks ORDER BY position)"))
            assertEquals("123", scalar(db, "SELECT deleted_at_epoch_ms FROM tasks WHERE id='trash'"))
            assertEquals("unassigned", scalar(db, "SELECT focus_mode FROM tasks WHERE id='trash'"))
            assertEquals("6789", scalar(db, "SELECT stopwatch_elapsed_ms FROM tasks WHERE id='watch'"))
            assertEquals("한글 ' \" 줄\n둘", scalar(db, "SELECT title FROM tasks WHERE id='timer'"))
            assertEquals("2", scalar(db, "SELECT count(*) FROM calendar_entries"))
            assertEquals(scheduled.body, scalar(db, "SELECT body FROM calendar_entries WHERE id='dated'"))
            assertEquals("2026-11-26", scalar(db, "SELECT end_date FROM calendar_entries WHERE id='dated'"))
            assertEquals("570", scalar(db, "SELECT end_minute FROM calendar_entries WHERE id='dated'"))
            assertEquals("1", scalar(db, "SELECT start_minute IS NULL AND end_minute IS NULL FROM calendar_entries WHERE id='all-day'"))
            assertEquals(category.color.toString(), scalar(db, "SELECT color_argb FROM calendar_categories WHERE position=0"))
            assertEquals("0,30,90", scalar(db, "SELECT group_concat(minutes,',') FROM (SELECT minutes FROM focus_presets ORDER BY position)"))
            assertEquals("8", scalar(db, "SELECT font_step FROM app_settings"))
            assertEquals("0", scalar(db, "SELECT wallpaper_enabled FROM app_settings"))
            assertEquals("watch", scalar(db, "SELECT selected_task_id FROM focus_state"))
            assertEquals("ok", scalar(db, "PRAGMA quick_check"))
            db.rawQuery("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
        }
        assertEquals(preferencesBefore, context.getSharedPreferences("focus", 0).all)
        assertEquals(scheduled, repo.get(scheduled.id))
        assertFalse(File(file.path + "-wal").exists()); assertFalse(File(file.path + "-shm").exists())
        val report = File("build/reports/backup/example-v1.db"); report.parentFile!!.mkdirs(); isolated.copyTo(report, overwrite = true)
        SQLiteDatabase.deleteDatabase(file); SQLiteDatabase.deleteDatabase(isolated); repo.close()
    }
    @Test fun runningStopwatchIsFrozenOnceInFileWhileLiveTimerContinues() {
        val store = Store(context)
        store.saveTasks(listOf(Task(id = "watch", text = "측정", stopwatch = true, stopwatchMs = 100)))
        val timer = TimerState(status = "running", mode = "stopwatch", taskId = "watch", elapsedMs = 2000, startedAt = System.currentTimeMillis() - 3000)
        store.timer = timer
        val snapshot = BackupSnapshot.capture(context)
        assertTrue(snapshot.tasks.single().stopwatchMs >= 5000)
        assertEquals(snapshot.tasks.single().stopwatchMs, snapshot.focus["elapsed_ms"])
        assertEquals("paused", snapshot.focus["status"])
        assertEquals(true, snapshot.focus["was_running"])
        assertEquals(timer, store.timer); assertEquals(100L, store.tasks().single().stopwatchMs)
    }
    @Test fun countdownExportRecordsRemainingDurationWithoutDeviceClockAnchors() {
        val store = Store(context)
        store.saveTasks(listOf(Task(id = "timer", text = "집중")))
        store.timer = TimerState(status = "running", taskId = "timer", endAt = System.currentTimeMillis() + 60000)
        val snapshot = BackupSnapshot.capture(context)
        assertEquals("paused", snapshot.focus["status"])
        assertTrue(snapshot.focus["remaining_ms"] as Long in 1..60000L)
        assertEquals("running", store.timer.status)
        val file = target(); BackupDatabase.write(context, file, snapshot)
        read(file) { db -> db.rawQuery("PRAGMA table_info(focus_state)", null).use { c ->
            val names = mutableListOf<String>(); while (c.moveToNext()) names.add(c.getString(1))
            assertFalse(names.contains("end_at")); assertFalse(names.contains("boot_count"))
        } }
        SQLiteDatabase.deleteDatabase(file)
    }
    @Test fun invalidRelationshipsAreRejectedInsteadOfProducingMisleadingBackup() {
        val snapshot = BackupSnapshot.capture(context)
        val file = target()
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            BackupDatabase.write(context, file, snapshot.copy(tasks = listOf(Task(text = "고아 항목", categoryId = "missing"))))
        }
        SQLiteDatabase.deleteDatabase(file)
    }
    @Test fun malformedTaskStorageIsNotSilentlyExportedAsEmpty() {
        context.getSharedPreferences("focus", 0).edit().putString("tasks", "invalid-json").commit()
        assertThrows(org.json.JSONException::class.java) { BackupSnapshot.capture(context) }
    }
    @Test fun settingsOpensSystemPickerAndCancellationDoesNotChangeData() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup(); val activity = controller.get()
        val root = activity.window.decorView
        find(root) { it.tag == "nav:settings" }!!.performClick()
        find(root) { it.contentDescription == "백업 파일로 내보내기" }!!.performClick()
        val picker = shadowOf(activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.intent.action)
        assertTrue(picker.intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertEquals("application/vnd.sqlite3", picker.intent.type)
        assertTrue(picker.intent.getStringExtra(Intent.EXTRA_TITLE)!!.endsWith(".db"))
        val before = context.getSharedPreferences("focus", 0).all.toMap()
        shadowOf(activity).receiveResult(picker.intent, Activity.RESULT_CANCELED, null)
        assertFalse(BackupExporter.isBusy()); assertEquals(before, context.getSharedPreferences("focus", 0).all)
        controller.pause().stop().destroy()
    }
    @Test fun systemPickerResultWritesStandaloneDatabaseAndWriteFailureReportsFailure() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup(); val activity = controller.get()
        val root = activity.window.decorView
        find(root) { it.tag == "nav:settings" }!!.performClick()
        find(root) { it.contentDescription == "백업 파일로 내보내기" }!!.performClick()
        val picker = shadowOf(activity).nextStartedActivityForResult
        val uri = Uri.parse("content://test.documents/backup.db")
        val closed = CountDownLatch(1)
        val output = object : ByteArrayOutputStream() { override fun close() { super.close(); closed.countDown() } }
        shadowOf(activity.contentResolver).registerOutputStream(uri, output)
        shadowOf(activity).receiveResult(picker.intent, Activity.RESULT_OK, Intent().setData(uri))
        assertTrue(closed.await(10, TimeUnit.SECONDS))
        fun finishWorker() {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (BackupExporter.isBusy() && System.nanoTime() < deadline) Thread.sleep(10)
            assertFalse(BackupExporter.isBusy()); shadowOf(Looper.getMainLooper()).idle()
        }
        finishWorker()
        assertEquals("백업 파일을 저장했습니다", ShadowToast.getTextOfLatestToast())
        assertEquals("SQLite format 3\u0000", output.toByteArray().take(16).toByteArray().toString(Charsets.US_ASCII))
        val file = target(); file.writeBytes(output.toByteArray())
        read(file) { db -> assertEquals("ok", scalar(db, "PRAGMA quick_check")) }
        SQLiteDatabase.deleteDatabase(file)
        val failed = CountDownLatch(1)
        shadowOf(activity.contentResolver).registerOutputStream(uri, object : java.io.OutputStream() {
            override fun write(value: Int) { throw IOException("Simulated storage failure") }
            override fun close() { failed.countDown() }
        })
        BackupExporter.export(activity, uri)
        assertTrue(failed.await(10, TimeUnit.SECONDS)); finishWorker()
        assertEquals("백업 파일을 저장하지 못했습니다", ShadowToast.getTextOfLatestToast())
        assertFalse(activity.cacheDir.listFiles().orEmpty().any { it.name.startsWith("jigeum-backup-") })
        controller.pause().stop().destroy()
    }
}
