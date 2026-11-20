package com.adastra.focuswallpaper

import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

const val NOTE_UNCATEGORIZED = "uncategorized"
data class NoteCategory(val id: String = UUID.randomUUID().toString(), val name: String, val color: Int)
data class NoteSchedule(val start: LocalDate, val end: LocalDate = start, val startMinute: Int? = null, val endMinute: Int? = null) {
    fun isValid() = !end.isBefore(start) && ((startMinute == null && endMinute == null) ||
        (startMinute != null && endMinute != null && startMinute in 0..1439 && endMinute in 0..1439 &&
            (end.isAfter(start) || endMinute >= startMinute)))
    fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
    fun overlaps(first: LocalDate, last: LocalDate) = !end.isBefore(first) && !start.isAfter(last)
}
// Shared note payload: calendar entries add a schedule; future ordinary notes leave it null.
data class Note(
    val id: String = UUID.randomUUID().toString(), val title: String = "", val body: String = "",
    val categoryId: String = NOTE_UNCATEGORIZED, val schedule: NoteSchedule? = null,
    val deletedAt: Long = 0, val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt, val bodyFormat: String = "plain"
) {
    val hasContent get() = title.isNotBlank() || body.isNotBlank()
    val displayTitle get() = title.ifBlank { body.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: "제목 없음" }
    fun trash(now: Long) = copy(deletedAt = now.coerceAtLeast(1), updatedAt = now)
    fun restore() = copy(deletedAt = 0)
}
object NoteCodec {
    fun encode(note: Note): String = JSONObject().apply {
        put("id", note.id); put("title", note.title); put("body", note.body); put("categoryId", note.categoryId)
        put("deletedAt", note.deletedAt); put("createdAt", note.createdAt); put("updatedAt", note.updatedAt); put("bodyFormat", note.bodyFormat)
        note.schedule?.let { s -> put("schedule", JSONObject().apply {
            put("start", s.start.toString()); put("end", s.end.toString())
            put("startMinute", s.startMinute ?: JSONObject.NULL); put("endMinute", s.endMinute ?: JSONObject.NULL)
        }) }
    }.toString()
    fun decode(value: String): Note {
        val o = JSONObject(value)
        val schedule = o.optJSONObject("schedule")?.let { s -> NoteSchedule(
            LocalDate.parse(s.getString("start")), LocalDate.parse(s.getString("end")),
            if (s.isNull("startMinute")) null else s.getInt("startMinute"),
            if (s.isNull("endMinute")) null else s.getInt("endMinute")
        ).also { require(it.isValid()) } }
        return Note(o.getString("id"), o.optString("title"), o.optString("body"), o.optString("categoryId", NOTE_UNCATEGORIZED),
            schedule, o.optLong("deletedAt"), o.optLong("createdAt"), o.optLong("updatedAt"), o.optString("bodyFormat", "plain"))
    }
}
object CalendarDates {
    fun gridStart(month: YearMonth): LocalDate {
        val first = month.atDay(1)
        return first.minusDays((first.dayOfWeek.value % 7).toLong())
    }
    fun cells(month: YearMonth) = (0L until 42L).map { gridStart(month).plusDays(it) }
    fun entries(notes: List<Note>, date: LocalDate, categoryId: String = ""): List<Note> = notes.filter {
        it.deletedAt == 0L && it.schedule?.contains(date) == true && (categoryId.isEmpty() || it.categoryId == categoryId)
    }.sortedWith(compareBy<Note> { it.schedule!!.startMinute ?: -1 }.thenBy { it.createdAt }.thenBy { it.id })
}
