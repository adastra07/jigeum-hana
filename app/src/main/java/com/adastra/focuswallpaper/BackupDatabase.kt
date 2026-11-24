package com.adastra.focuswallpaper

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import java.io.File

/** Immutable point-in-time values, independent from the live preferences/database afterwards. */
data class BackupSnapshot(
    val createdAt: Long, val appVersion: String,
    val taskCategories: List<TaskCategory>, val tasks: List<Task>,
    val calendarCategories: List<NoteCategory>, val entries: List<Note>,
    val settings: Map<String, Any>, val presets: List<Int>, val focus: Map<String, Any?>
) {
    companion object {
        fun capture(context: Context): BackupSnapshot {
            val store = Store(context)
            val now = System.currentTimeMillis()
            val timer = store.timer
            val elapsed = timer.elapsed(now, SystemClock.elapsedRealtime(), Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1))
            val rawTasks = context.getSharedPreferences("focus", Context.MODE_PRIVATE).getString("tasks", "[]").orEmpty()
            val tasks = TaskCodec.decode(rawTasks)
            require(tasks.size == JSONArray(rawTasks).length()) { "Invalid task records" }
            val frozenTasks = tasks.map {
                if (timer.isStopwatch && timer.taskId == it.id && timer.status in listOf("running", "paused")) it.copy(stopwatchMs = elapsed) else it.copy()
            }
            val rawCategories = JSONArray(context.getSharedPreferences("focus", Context.MODE_PRIVATE).getString("categories", "[]"))
            val categoryIds = (0 until rawCategories.length()).map { index ->
                val value = rawCategories.getJSONObject(index)
                require(value.getString("name").isNotBlank())
                value.getString("id").also { require(it.isNotBlank()) }
            }
            require(categoryIds.distinct().size == categoryIds.size) { "Duplicate category IDs" }
            val categories = store.categories()
            val repo = NoteRepository(context)
            val calendarCategories: List<NoteCategory>
            val entries: List<Note>
            try {
                val db = repo.readableDatabase
                db.beginTransactionNonExclusive()
                try {
                    calendarCategories = repo.categories()
                    entries = repo.notes(includeTrash = true)
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            } finally { repo.close() }
            val ids = tasks.map { it.id }.toSet()
            val remaining = if (timer.isStopwatch) 0 else timer.remaining(now)
            val status = when {
                timer.status == "running" && !timer.isStopwatch && remaining == 0L -> "finished"
                timer.status == "running" -> "paused"
                else -> timer.status
            }
            return BackupSnapshot(now, context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
                categories, frozenTasks, calendarCategories, entries,
                mapOf("default_focus_mode" to store.defaultFocusMode, "font_step" to store.fontStep,
                    "last_minutes" to store.lastMinutes, "last_stopwatch" to store.lastStopwatch,
                    "wallpaper_enabled" to store.wallpaperEnabled, "wallpaper_color_argb" to store.wallpaperColor,
                    "calendar_hide_uncategorized" to store.hideCalendarUncategorized,
                    "completed_tasks_last" to store.completedTasksLast,
                    "checklist_start_mode" to store.checklistStartMode, "calendar_start_mode" to store.calendarStartMode,
                    "last_calendar_category_id" to store.lastCalendarCategory.takeIf { id -> calendarCategories.any { it.id == id } }.orEmpty()),
                store.presets.toList(), mapOf(
                    "selected_task_id" to store.selected.takeIf { it in ids },
                    "selected_category_id" to store.category.takeIf { id -> categories.any { it.id == id } }.orEmpty().ifEmpty { DEFAULT_CATEGORY },
                    "timer_task_id" to timer.taskId.takeIf { it in ids }, "title" to timer.title,
                    "mode" to timer.mode, "status" to status, "was_running" to (timer.status == "running"),
                    "remaining_ms" to remaining, "elapsed_ms" to elapsed))
        }
    }
}

object BackupDatabase {
    const val SCHEMA_VERSION = 1
    const val APPLICATION_ID = 0x4a484e41
    private fun values(fields: Map<String, Any?>) = ContentValues().apply {
        fields.forEach { (key, value) -> when (value) {
            null -> putNull(key)
            is String -> put(key, value)
            is Int -> put(key, value)
            is Long -> put(key, value)
            is Boolean -> put(key, if (value) 1 else 0)
            else -> error("Unsupported backup value")
        } }
    }
    fun write(context: Context, file: File, snapshot: BackupSnapshot) {
        require(!file.exists() || file.length() == 0L) { "Backup destination is not empty" }
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        try {
            db.disableWriteAheadLogging()
            db.rawQuery("PRAGMA journal_mode=DELETE", null).use { check(it.moveToFirst() && it.getString(0).equals("delete", true)) }
            db.setForeignKeyConstraintsEnabled(true)
            db.beginTransaction()
            try {
                context.assets.open("backup-schema-v1.sql").bufferedReader().use { it.readText() }.split(';')
                    .map { it.trim() }.filter { it.isNotEmpty() }.forEach { db.execSQL(it) }
                db.version = SCHEMA_VERSION
                db.execSQL("PRAGMA application_id=$APPLICATION_ID")
                fun insert(table: String, fields: Map<String, Any?>) { db.insertOrThrow(table, null, values(fields)) }
                insert("backup_info", mapOf("id" to 1, "format" to "jigeum-hana", "schema_version" to SCHEMA_VERSION,
                    "app_version" to snapshot.appVersion, "created_at_epoch_ms" to snapshot.createdAt))
                snapshot.taskCategories.forEachIndexed { i, c -> insert("task_categories", mapOf("id" to c.id, "name" to c.name, "position" to i)) }
                snapshot.tasks.forEachIndexed { i, t -> insert("tasks", mapOf("id" to t.id, "title" to t.text, "category_id" to t.categoryId,
                    "position" to i, "completed" to t.done, "focus_mode" to (if (t.stopwatch) "stopwatch" else if (t.minutes == 0) "unassigned" else "timer"),
                    "focus_minutes" to t.minutes, "stopwatch_elapsed_ms" to t.stopwatchMs, "deleted_at_epoch_ms" to t.deletedAt, "completed_at_epoch_ms" to t.completedAt)) }
                snapshot.calendarCategories.forEachIndexed { i, c -> insert("calendar_categories", mapOf("id" to c.id, "name" to c.name, "color_argb" to c.color, "position" to i)) }
                snapshot.entries.forEach { n ->
                    val s = requireNotNull(n.schedule)
                    require(s.isValid())
                    insert("calendar_entries", mapOf("id" to n.id, "title" to n.title, "body" to n.body, "category_id" to n.categoryId,
                        "start_date" to s.start.toString(), "end_date" to s.end.toString(), "start_minute" to s.startMinute, "end_minute" to s.endMinute,
                        "deleted_at_epoch_ms" to n.deletedAt, "created_at_epoch_ms" to n.createdAt, "updated_at_epoch_ms" to n.updatedAt, "body_format" to n.bodyFormat))
                }
                insert("app_settings", snapshot.settings + ("id" to 1))
                require(snapshot.presets.size == 3)
                snapshot.presets.forEachIndexed { i, m -> insert("focus_presets", mapOf("position" to i, "minutes" to m)) }
                insert("focus_state", snapshot.focus + ("id" to 1))
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        } finally { db.close() }
        // Verify the standalone file after closing the writer, without a WAL/SHM companion.
        val reader = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        try {
            reader.rawQuery("PRAGMA quick_check", null).use { check(it.moveToFirst() && it.getString(0) == "ok") }
            reader.rawQuery("PRAGMA foreign_key_check", null).use { check(!it.moveToFirst()) }
        } finally { reader.close() }
    }
}
