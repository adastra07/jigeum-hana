package com.adastra.focuswallpaper

object TaskOrder {
    /** Keep the saved order as the return position; completion only changes the projection. */
    fun visible(tasks: List<Task>, categoryId: String, completedLast: Boolean): List<Task> {
        val active = tasks.filter { it.categoryId == categoryId && it.deletedAt == 0L }
        return if (!completedLast) active else active.filterNot { it.done } + active.filter { it.done }.sortedBy { it.completedAt }
    }
    fun setCompleted(tasks: List<Task>, taskId: String, completed: Boolean, now: Long): List<Task> {
        val latest = tasks.maxOfOrNull { it.completedAt } ?: 0L
        val stamp = maxOf(now, if (latest < Long.MAX_VALUE) latest + 1 else latest, 1L)
        return tasks.map { if (it.id == taskId && it.deletedAt == 0L && it.done != completed)
            it.copy(done = completed, completedAt = if (completed) stamp else 0) else it }
    }
    // Replace only this category's active slots, using current objects so live records survive.
    fun reorder(tasks: List<Task>, categoryId: String, ids: List<String>, completedLast: Boolean = false): List<Task> {
        val visible = tasks.filter { it.categoryId == categoryId && it.deletedAt == 0L }
        if (ids.size != visible.size || ids.toSet().size != ids.size || ids.toSet() != visible.map { it.id }.toSet()) return tasks
        val byId = visible.associateBy { it.id }
        if (completedLast) {
            val active = ids.map { byId.getValue(it) }.filterNot { it.done }.iterator()
            val completed = ids.map { byId.getValue(it) }.filter { it.done }
                .mapIndexed { index, task -> task.id to index.toLong() + 1 }.toMap()
            // Dragging within a group keeps the other group's original return slots.
            return tasks.map { if (it.categoryId != categoryId || it.deletedAt != 0L) it else if (it.done) it.copy(completedAt = completed.getValue(it.id)) else active.next() }
        }
        val ordered = ids.map { byId.getValue(it) }.iterator()
        return tasks.map { if (it.categoryId == categoryId && it.deletedAt == 0L) ordered.next() else it }
    }
}
