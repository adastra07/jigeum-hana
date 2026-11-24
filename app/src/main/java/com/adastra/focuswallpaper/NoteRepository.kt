package com.adastra.focuswallpaper

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Separate additive database; existing checklist preferences and timer records are untouched. */
class NoteRepository(context: Context) : SQLiteOpenHelper(context.applicationContext, "notes.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE note_categories (id TEXT PRIMARY KEY, name TEXT NOT NULL, color INTEGER NOT NULL, position INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE notes (id TEXT PRIMARY KEY, payload TEXT NOT NULL, category_id TEXT NOT NULL, date_start TEXT, date_end TEXT, deleted_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX note_dates ON notes(deleted_at,date_start,date_end)")
        db.insertOrThrow("note_categories", null, ContentValues().apply {
            put("id", NOTE_UNCATEGORIZED); put("name", "미분류"); put("color", 0xffc8d6ef.toInt()); put("position", 0)
        })
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Unsupported note database migration: $oldVersion to $newVersion")
    }
    fun categories(): List<NoteCategory> = readableDatabase.rawQuery("SELECT id,name,color FROM note_categories ORDER BY position,id", null).use { c ->
        buildList { while (c.moveToNext()) add(NoteCategory(c.getString(0), c.getString(1), c.getInt(2))) }
    }
    fun saveCategory(category: NoteCategory) {
        val db = writableDatabase
        val position = db.rawQuery("SELECT position FROM note_categories WHERE id=?", arrayOf(category.id)).use { c -> if (c.moveToFirst()) c.getInt(0) else null }
            ?: db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM note_categories", null).use { c -> c.moveToFirst(); c.getInt(0) }
        db.insertWithOnConflict("note_categories", null, ContentValues().apply {
            put("id", category.id); put("name", category.name); put("color", category.color); put("position", position)
        }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
    }
    fun moveCategory(id: String, direction: Int, hiddenIds: Set<String> = emptySet()) {
        val all = categories(); val visible = all.filterNot { it.id in hiddenIds }
        val visibleIndex = visible.indexOfFirst { it.id == id }; val neighbour = visibleIndex + direction
        if (direction !in listOf(-1, 1) || visibleIndex < 0 || neighbour !in visible.indices) return
        val index = all.indexOfFirst { it.id == id }; val target = all.indexOfFirst { it.id == visible[neighbour].id }
        val order = all.toMutableList().apply { val other = this[target]; this[target] = this[index]; this[index] = other }
        val db = writableDatabase; db.beginTransaction()
        try {
            order.forEachIndexed { i, category -> db.update("note_categories", ContentValues().apply { put("position", i) }, "id=?", arrayOf(category.id)) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun removeCategory(id: String) {
        require(id != NOTE_UNCATEGORIZED)
        val db = writableDatabase; db.beginTransaction()
        try {
            notes(includeTrash = true, calendarOnly = false).filter { it.categoryId == id }.forEach { save(it.copy(categoryId = NOTE_UNCATEGORIZED)) }
            db.delete("note_categories", "id=?", arrayOf(id)); db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun notes(includeTrash: Boolean = false, calendarOnly: Boolean = true): List<Note> {
        val clauses = mutableListOf<String>()
        if (!includeTrash) clauses.add("deleted_at=0")
        if (calendarOnly) clauses.add("date_start IS NOT NULL")
        val where = if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ")
        return readableDatabase.rawQuery("SELECT payload FROM notes$where ORDER BY updated_at DESC,id", null).use { c ->
            buildList { while (c.moveToNext()) add(NoteCodec.decode(c.getString(0))) }
        }
    }
    fun get(id: String): Note? = readableDatabase.rawQuery("SELECT payload FROM notes WHERE id=?", arrayOf(id)).use { c ->
        if (c.moveToFirst()) NoteCodec.decode(c.getString(0)) else null
    }
    fun save(note: Note) {
        require(note.schedule?.isValid() != false)
        val values = ContentValues().apply {
            put("id", note.id); put("payload", NoteCodec.encode(note)); put("category_id", note.categoryId)
            put("date_start", note.schedule?.start?.toString()); put("date_end", note.schedule?.end?.toString())
            put("deleted_at", note.deletedAt); put("updated_at", note.updatedAt)
        }
        writableDatabase.insertWithOnConflict("notes", null, values, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
    }
    fun permanentlyDelete(id: String) { writableDatabase.delete("notes", "id=? AND deleted_at>0 AND date_start IS NOT NULL", arrayOf(id)) }
    fun emptyCalendarTrash() { writableDatabase.delete("notes", "deleted_at>0 AND date_start IS NOT NULL", null) }
}
