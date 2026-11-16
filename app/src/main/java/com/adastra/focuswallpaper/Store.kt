package com.adastra.focuswallpaper

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

const val DEFAULT_CATEGORY = "default"
data class Task(
    val id: String = UUID.randomUUID().toString(), var text: String,
    var done: Boolean = false, var minutes: Int = 25,
    val categoryId: String = DEFAULT_CATEGORY, val deletedAt: Long = 0,
    val stopwatch: Boolean = false, val stopwatchMs: Long = 0
) {
    val hasTimer: Boolean get() = !stopwatch && minutes > 0
    val hasClock: Boolean get() = stopwatch || hasTimer
    fun trash(now: Long) = copy(deletedAt = now.coerceAtLeast(1))
    fun restore() = copy(deletedAt = 0)
}
data class TaskCategory(val id: String = UUID.randomUUID().toString(), val name: String)

// New fields are optional when reading the original APK's JSON.
object TaskCodec {
    fun decode(json: String): MutableList<Task> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isEmpty()) null else Task(id, o.optString("text"), o.optBoolean("done"),
                o.optInt("minutes", 25).coerceIn(0, 180), o.optString("categoryId", DEFAULT_CATEGORY), o.optLong("deletedAt", 0),
                o.optBoolean("stopwatch", false), o.optLong("stopwatchMs", 0).coerceAtLeast(0))
        }.toMutableList()
    }
    fun encode(tasks: List<Task>): String = JSONArray().apply {
        tasks.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)
            .put("minutes", it.minutes).put("categoryId", it.categoryId).put("deletedAt", it.deletedAt).put("stopwatch", it.stopwatch).put("stopwatchMs", it.stopwatchMs)) }
    }.toString()
}
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("focus", Context.MODE_PRIVATE)
    var selected: String
        get() = prefs.getString("selected", "").orEmpty()
        set(value) { prefs.edit().putString("selected", value).commit() }
    var category: String
        get() = prefs.getString("category", DEFAULT_CATEGORY).orEmpty()
        set(value) { prefs.edit().putString("category", value).commit() }
    var timer: TimerState
        get() = TimerState(prefs.getString("status", "idle").orEmpty(), prefs.getLong("end", 0),
            prefs.getLong("remaining", 0), prefs.getString("title", "").orEmpty(), prefs.getString("timerTaskId", "").orEmpty(),
            prefs.getString("timerMode", "timer").orEmpty(), prefs.getLong("stopwatchElapsed", 0),
            prefs.getLong("stopwatchStarted", 0), prefs.getLong("stopwatchRealtime", -1), prefs.getInt("stopwatchBoot", -1))
        set(value) { timerEditor(value).commit() }
    private fun timerEditor(value: TimerState): SharedPreferences.Editor = prefs.edit()
        .putString("status", value.status).putLong("end", value.endAt).putLong("remaining", value.pausedMs)
        .putString("title", value.title).putString("timerTaskId", value.taskId).putString("timerMode", value.mode)
        .putLong("stopwatchElapsed", value.elapsedMs).putLong("stopwatchStarted", value.startedAt)
        .putLong("stopwatchRealtime", value.startedRealtime).putInt("stopwatchBoot", value.bootCount)
    fun saveTimerAndTasks(value: TimerState, tasks: List<Task>) {
        timerEditor(value).putString("tasks", TaskCodec.encode(tasks)).commit()
    }
    var lastStopwatch: Boolean
        get() = prefs.getBoolean("lastStopwatch", false)
        set(value) { prefs.edit().putBoolean("lastStopwatch", value).commit() }
    var lastMinutes: Int
        get() = prefs.getInt("lastMinutes", presets[1]).coerceIn(0, 180)
        set(value) { require(value in 0..180); prefs.edit().putInt("lastMinutes", value).commit() }
    var wallpaperEnabled: Boolean
        get() = prefs.getBoolean("wallpaperEnabled", true)
        set(value) { prefs.edit().putBoolean("wallpaperEnabled", value).commit() }
    var wallpaperColor: Int
        get() = prefs.getInt("wallpaperColor", WallpaperPalette.colors.first().second)
        set(value) { prefs.edit().putInt("wallpaperColor", value).commit() }
    // -1 denotes a pre-tracking installation; 0 is cleared, 1 is app-applied focus text.
    var focusWallpaperState: Int
        get() = prefs.getInt("focusWallpaperState", -1)
        set(value) { prefs.edit().putInt("focusWallpaperState", value).commit() }
    var appliedWallpaperColor: Int
        get() = prefs.getInt("appliedWallpaperColor", wallpaperColor)
        set(value) { prefs.edit().putInt("appliedWallpaperColor", value).commit() }
    var presets: List<Int>
        get() = runCatching {
            val a = JSONArray(prefs.getString("presets", "[5,25,60]"))
            require(a.length() == 3)
            (0..2).map { a.getInt(it).also { n -> require(n in 0..180) } }
        }.getOrDefault(listOf(5, 25, 60))
        set(value) {
            require(value.size == 3 && value.all { it in 0..180 })
            prefs.edit().putString("presets", JSONArray(value).toString()).commit()
        }
    fun categories(): List<TaskCategory> = runCatching {
        val array = JSONArray(prefs.getString("categories", "[]"))
        val result = (0 until array.length()).mapNotNull {
            val o = array.optJSONObject(it) ?: return@mapNotNull null
            val id = o.optString("id"); val name = o.optString("name")
            if (id.isBlank() || name.isBlank()) null else TaskCategory(id, name)
        }.distinctBy { it.id }
        if (result.any { it.id == DEFAULT_CATEGORY }) result else listOf(TaskCategory(DEFAULT_CATEGORY, "할 일")) + result
    }.getOrDefault(listOf(TaskCategory(DEFAULT_CATEGORY, "할 일")))
    fun saveCategories(categories: List<TaskCategory>) {
        require(categories.any { it.id == DEFAULT_CATEGORY })
        val array = JSONArray()
        categories.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name)) }
        prefs.edit().putString("categories", array.toString()).commit()
    }
    fun removeCategory(id: String) {
        require(id != DEFAULT_CATEGORY)
        val tasks = tasks().map { if (it.categoryId == id) it.copy(categoryId = DEFAULT_CATEGORY) else it }
        val categories = categories().filterNot { it.id == id }
        val array = JSONArray()
        categories.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name)) }
        // Change task assignments and tabs in one transaction, including trash items.
        val edit = prefs.edit().putString("tasks", TaskCodec.encode(tasks)).putString("categories", array.toString())
        if (category == id) edit.putString("category", DEFAULT_CATEGORY)
        edit.commit()
    }
    fun tasks(): MutableList<Task> = try { TaskCodec.decode(prefs.getString("tasks", "[]").orEmpty()) }
        catch (_: org.json.JSONException) { mutableListOf() }
    fun saveTasks(tasks: List<Task>) { prefs.edit().putString("tasks", TaskCodec.encode(tasks)).commit() }
}
