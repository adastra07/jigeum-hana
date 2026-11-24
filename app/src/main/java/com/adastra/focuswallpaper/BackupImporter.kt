package com.adastra.focuswallpaper

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Only the application's versioned, standalone format is accepted, never a live/source DB. */
object BackupImporter {
    const val REQUEST_CODE = 1270
    private val busy = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    fun isBusy() = busy.get()
    fun openDocumentIntent() = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        // Document providers use different MIME types for .db files.
        type = "*/*"
    }
    fun select(activity: Activity, source: Uri, restored: () -> Unit) {
        if (BackupExporter.isBusy() || !busy.compareAndSet(false, true)) return
        val app = activity.applicationContext
        Toast.makeText(app, "가져올 파일을 확인하고 있습니다", Toast.LENGTH_SHORT).show()
        worker.execute {
            var temp: File? = null
            val result = runCatching {
                temp = File.createTempFile("jigeum-import-", ".db", app.cacheDir)
                app.contentResolver.openInputStream(source).use { input ->
                    requireNotNull(input)
                    temp!!.outputStream().use { output ->
                        val buffer = ByteArray(8192); var total = 0L
                        while (true) {
                            val count = input.read(buffer); if (count < 0) break
                            total += count; require(total <= 64L * 1024 * 1024) { "File too large" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                read(app, temp!!)
            }
            temp?.let { SQLiteDatabase.deleteDatabase(it) }
            main.post {
                if (activity.isFinishing || activity.isDestroyed) { busy.set(false); return@post }
                result.fold({ snapshot ->
                    val dialog = AlertDialog.Builder(activity).setTitle("백업을 가져올까요?")
                        .setMessage("현재 할 일·캘린더·휴지통·설정을 이 파일의 데이터로 교체합니다.\n할 일 ${snapshot.tasks.size}개 · 캘린더 ${snapshot.entries.size}개 (휴지통 포함)\n\n필요한 현재 데이터는 먼저 내보내기로 보관해 주세요. 타이머는 일시정지 상태로 가져옵니다.")
                        .setNegativeButton("취소", null)
                        .setPositiveButton("가져오기") { _, _ ->
                            val success = runCatching { restore(app, snapshot) }.isSuccess
                            busy.set(false)
                            if (success) { TimerEngine.sync(app); restored() }
                            Toast.makeText(app, if (success) "백업을 가져왔습니다" else "가져오지 못했습니다. 기존 데이터를 유지합니다", Toast.LENGTH_LONG).show()
                        }.create()
                    dialog.setOnDismissListener { busy.set(false) }
                    dialog.show()
                }, {
                    busy.set(false)
                    Toast.makeText(app, "가져올 수 없는 파일입니다. 지금 하나 백업 DB 파일을 선택해 주세요", Toast.LENGTH_LONG).show()
                })
            }
        }
    }

    fun read(context: Context, file: File): BackupSnapshot {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        val snapshot: BackupSnapshot
        try {
            require(db.version == BackupDatabase.SCHEMA_VERSION)
            db.rawQuery("PRAGMA application_id", null).use { require(it.moveToFirst() && it.getInt(0) == BackupDatabase.APPLICATION_ID) }
            db.rawQuery("PRAGMA quick_check", null).use { require(it.moveToFirst() && it.getString(0) == "ok") }
            db.rawQuery("PRAGMA foreign_key_check", null).use { require(!it.moveToFirst()) }
            // Reject views, triggers and missing tables, even when the filename looks correct.
            val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c -> buildSet { while(c.moveToNext()) add(c.getString(0)) } }
            require(tables.containsAll(listOf("backup_info", "task_categories", "tasks", "calendar_categories", "calendar_entries", "app_settings", "focus_presets", "focus_state")))
            db.rawQuery("SELECT name FROM sqlite_master WHERE type IN ('view','trigger')", null).use { require(!it.moveToFirst()) }
            fun <T> rows(table: String, order: String = "id", block: (Cursor) -> T): List<T> =
                db.rawQuery("SELECT * FROM $table ORDER BY $order", null).use { c -> buildList { while(c.moveToNext()) add(block(c)) } }
            val info = rows("backup_info") { c ->
                require(c.int("id") == 1 && c.text("format") == "jigeum-hana" && c.int("schema_version") == 1)
                c.long("created_at_epoch_ms") to c.text("app_version")
            }.single()
            val categories = rows("task_categories", "position") { c -> TaskCategory(c.text("id"), c.text("name")) }
            val tasks = rows("tasks", "position") { c ->
                val mode = c.text("focus_mode"); val minutes = c.int("focus_minutes")
                require(mode in listOf("timer", "stopwatch", "unassigned"))
                require(mode == "stopwatch" || (mode == "unassigned") == (minutes == 0))
                Task(c.text("id"), c.text("title"), c.bool("completed"), minutes, c.text("category_id"), c.long("deleted_at_epoch_ms"), mode == "stopwatch", c.long("stopwatch_elapsed_ms"),
                    if (c.getColumnIndex("completed_at_epoch_ms") >= 0) c.long("completed_at_epoch_ms") else 0)
            }
            val noteCategories = rows("calendar_categories", "position") { c -> NoteCategory(c.text("id"), c.text("name"), c.int("color_argb")) }
            val notes = rows("calendar_entries") { c ->
                val schedule = NoteSchedule(LocalDate.parse(c.text("start_date")), LocalDate.parse(c.text("end_date")), c.optionalInt("start_minute"), c.optionalInt("end_minute"))
                require(schedule.isValid() && c.text("body_format") == "plain")
                Note(c.text("id"), c.text("title"), c.text("body"), c.text("category_id"), schedule, c.long("deleted_at_epoch_ms"), c.long("created_at_epoch_ms"), c.long("updated_at_epoch_ms"), c.text("body_format"))
            }
            val settings = rows("app_settings") { c ->
                require(c.int("id") == 1)
                mapOf<String, Any>("default_focus_mode" to c.text("default_focus_mode"), "font_step" to c.int("font_step"), "last_minutes" to c.int("last_minutes"),
                    "last_stopwatch" to c.bool("last_stopwatch"), "wallpaper_enabled" to c.bool("wallpaper_enabled"), "wallpaper_color_argb" to c.int("wallpaper_color_argb"),
                    "calendar_hide_uncategorized" to if (c.getColumnIndex("calendar_hide_uncategorized") >= 0) c.bool("calendar_hide_uncategorized") else false,
                    "completed_tasks_last" to if (c.getColumnIndex("completed_tasks_last") >= 0) c.bool("completed_tasks_last") else true)
            }.single()
            val presets = rows("focus_presets", "position") { c -> c.int("position") to c.int("minutes") }
            require(presets.map { it.first } == listOf(0, 1, 2))
            val focus = rows("focus_state") { c ->
                require(c.int("id") == 1)
                mapOf<String, Any?>("selected_task_id" to c.optionalText("selected_task_id"), "selected_category_id" to c.text("selected_category_id"),
                    "timer_task_id" to c.optionalText("timer_task_id"), "title" to c.text("title"), "mode" to c.text("mode"), "status" to c.text("status"),
                    "was_running" to c.bool("was_running"), "remaining_ms" to c.long("remaining_ms"), "elapsed_ms" to c.long("elapsed_ms"))
            }.single()
            require(categories.any { it.id == DEFAULT_CATEGORY } && noteCategories.any { it.id == NOTE_UNCATEGORIZED })
            require(categories.all { it.id.isNotBlank() && it.name.isNotBlank() } && noteCategories.all { it.id.isNotBlank() && it.name.isNotBlank() })
            require(tasks.all { it.id.isNotBlank() } && notes.all { it.id.isNotBlank() })
            if (focus["status"] != "idle") {
                val task = tasks.single { it.id == focus["timer_task_id"] }
                require(task.deletedAt == 0L && task.hasClock && task.stopwatch == (focus["mode"] == "stopwatch"))
            }
            snapshot = BackupSnapshot(info.first, info.second, categories, tasks, noteCategories, notes, settings, presets.map { it.second }, focus)
        } finally { db.close() }
        // Rebuild using our own constraints: input files cannot weaken CHECK/FK constraints.
        val validated = File.createTempFile("jigeum-validate-", ".db", context.cacheDir)
        try { BackupDatabase.write(context, validated, snapshot) } finally { SQLiteDatabase.deleteDatabase(validated) }
        return snapshot
    }
    private fun Cursor.text(name: String): String { val i = getColumnIndexOrThrow(name); require(getType(i) == Cursor.FIELD_TYPE_STRING); return getString(i) }
    private fun Cursor.long(name: String): Long { val i = getColumnIndexOrThrow(name); require(getType(i) == Cursor.FIELD_TYPE_INTEGER); return getLong(i) }
    private fun Cursor.int(name: String): Int = long(name).also { require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) }.toInt()
    private fun Cursor.bool(name: String): Boolean = int(name).also { require(it in 0..1) } == 1
    private fun Cursor.optionalText(name: String) = if (isNull(getColumnIndexOrThrow(name))) null else text(name)
    private fun Cursor.optionalInt(name: String) = if (isNull(getColumnIndexOrThrow(name))) null else int(name)

    /** Called on the main thread, preventing editors/timer receivers from interleaving writes. */
    fun restore(context: Context, snapshot: BackupSnapshot) {
        recover(context)
        val prefs = context.getSharedPreferences("focus", Context.MODE_PRIVATE)
        val previous = prefs.all.toMap()
        val repo = NoteRepository(context)
        val db = repo.writableDatabase
        var preferencesWritten = false
        try {
            db.beginTransaction()
            try {
                // Preserve future ordinary notes, which the version-1 backup does not include.
                db.delete("notes", "date_start IS NOT NULL", null)
                db.delete("note_categories", null, null)
                snapshot.calendarCategories.forEach { repo.saveCategory(it) }
                snapshot.entries.forEach { note ->
                    require(repo.get(note.id) == null) { "ID belongs to an ordinary note" }
                    repo.save(note)
                }
                val categoryJson = JSONArray().apply { snapshot.taskCategories.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } }
                val f = snapshot.focus; val s = snapshot.settings
                val edit = prefs.edit().putString("tasks", TaskCodec.encode(snapshot.tasks)).putString("categories", categoryJson.toString())
                    .putString("presets", JSONArray(snapshot.presets).toString()).putString("selected", f["selected_task_id"] as? String ?: "")
                    .putString("category", f["selected_category_id"] as String).putString("status", f["status"] as String)
                    .putString("title", f["title"] as String).putString("timerTaskId", f["timer_task_id"] as? String ?: "")
                    .putString("timerMode", f["mode"] as String).putLong("end", 0).putLong("remaining", f["remaining_ms"] as Long)
                    .putLong("stopwatchElapsed", f["elapsed_ms"] as Long).putLong("stopwatchStarted", 0)
                    .putLong("stopwatchRealtime", -1).putInt("stopwatchBoot", -1)
                    .putString("defaultFocusMode", s["default_focus_mode"] as String).putInt("fontStep", s["font_step"] as Int)
                    .putInt("lastMinutes", s["last_minutes"] as Int).putBoolean("lastStopwatch", s["last_stopwatch"] as Boolean)
                    .putBoolean("wallpaperEnabled", s["wallpaper_enabled"] as Boolean).putInt("wallpaperColor", s["wallpaper_color_argb"] as Int)
                    .putBoolean("hideCalendarUncategorized", s["calendar_hide_uncategorized"] as? Boolean ?: false)
                    .putBoolean("completedTasksLast", s["completed_tasks_last"] as? Boolean ?: true)
                // A durable undo record covers process death between the two storage commits.
                writeUndo(context, previous)
                preferencesWritten = true
                check(edit.commit()) { "Preferences write failed" }
                db.execSQL("CREATE TABLE IF NOT EXISTS import_commits (token TEXT PRIMARY KEY)")
                db.execSQL("INSERT OR REPLACE INTO import_commits(token) VALUES ('pending')")
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        } catch (error: Exception) {
            if (preferencesWritten) {
                restorePreferences(context, previous)
                android.util.AtomicFile(File(context.filesDir, "import-undo.json")).delete()
            }
            throw error
        } finally { repo.close() }
        // Leave the journal until both commits have completed; startup can finish cleanup.
        recover(context)
        context.getSystemService(android.app.NotificationManager::class.java).cancel(101)
    }
    private fun writeUndo(context: Context, previous: Map<String, *>) {
        val file = android.util.AtomicFile(File(context.filesDir, "import-undo.json"))
        val data = JSONObject().apply { previous.forEach { (key, value) ->
            put(key, JSONObject().put("type", when(value) { is Long -> "long"; is Int -> "int"; is Boolean -> "bool"; is String -> "string"; else -> error("Unsupported preference") }).put("value", value))
        } }.toString().toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try { output.write(data); file.finishWrite(output) } catch (e: Exception) { file.failWrite(output); throw e }
    }
    private fun restorePreferences(context: Context, previous: Map<String, *>) {
        val edit = context.getSharedPreferences("focus", 0).edit().clear()
        previous.forEach { (key, value) -> when (value) {
            is String -> edit.putString(key, value); is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value); is Long -> edit.putLong(key, value)
            else -> error("Unsupported preference")
        } }
        check(edit.commit())
    }
    @Synchronized fun recover(context: Context) {
        val file = android.util.AtomicFile(File(context.filesDir, "import-undo.json"))
        val journalExists = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
        val repo = NoteRepository(context)
        try {
            val db = repo.writableDatabase
            val committed = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='import_commits'", null).use { it.moveToFirst() } &&
                db.rawQuery("SELECT token FROM import_commits WHERE token='pending'", null).use { it.moveToFirst() }
            if (journalExists && !committed) {
                val json = JSONObject(file.openRead().bufferedReader().use { it.readText() })
                val previous = json.keys().asSequence().associateWith { key -> val v = json.getJSONObject(key); when(v.getString("type")) {
                    "long" -> v.getLong("value"); "int" -> v.getInt("value"); "bool" -> v.getBoolean("value"); "string" -> v.getString("value"); else -> error("Invalid undo record")
                } }
                restorePreferences(context, previous)
            }
            file.delete()
            if (committed) db.delete("import_commits", "token='pending'", null)
        } finally { repo.close() }
    }
}
