package com.adastra.focuswallpaper

object TaskOrder {
    // Replace only this category's active slots, using current objects so live records survive.
    fun reorder(tasks: List<Task>, categoryId: String, ids: List<String>): List<Task> {
        val visible = tasks.filter { it.categoryId == categoryId && it.deletedAt == 0L }
        if (ids.size != visible.size || ids.toSet().size != ids.size || ids.toSet() != visible.map { it.id }.toSet()) return tasks
        val byId = visible.associateBy { it.id }
        val ordered = ids.map { byId.getValue(it) }.iterator()
        return tasks.map { if (it.categoryId == categoryId && it.deletedAt == 0L) ordered.next() else it }
    }
}
