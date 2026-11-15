package com.adastra.focuswallpaper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Task(val id: String = UUID.randomUUID().toString(), var text: String, var done: Boolean = false, var minutes: Int = 25)

// Preserve the APK's preference file and field names. A matching signing key is still required for upgrades.
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("focus", Context.MODE_PRIVATE)
    var selected: String
        get() = prefs.getString("selected", "").orEmpty()
        set(value) { prefs.edit().putString("selected", value).commit() }
    var timer: TimerState
        get() = TimerState(prefs.getString("status", "idle").orEmpty(), prefs.getLong("end", 0), prefs.getLong("remaining", 0), prefs.getString("title", "").orEmpty(), prefs.getString("timerTaskId", "").orEmpty())
        set(value) { prefs.edit().putString("status", value.status).putLong("end", value.endAt).putLong("remaining", value.pausedMs).putString("title", value.title).putString("timerTaskId", value.taskId).commit() }
    fun tasks(): MutableList<Task> {
        val array = try { JSONArray(prefs.getString("tasks", "[]")) } catch (_: org.json.JSONException) { return mutableListOf() }
        return (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isEmpty()) null else Task(id, o.optString("text"), o.optBoolean("done"), o.optInt("minutes", 25).coerceIn(1, 180))
        }.toMutableList()
    }
    fun saveTasks(tasks: List<Task>) {
        val array = JSONArray()
        tasks.forEach { array.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done).put("minutes", it.minutes)) }
        prefs.edit().putString("tasks", array.toString()).commit()
    }
}
