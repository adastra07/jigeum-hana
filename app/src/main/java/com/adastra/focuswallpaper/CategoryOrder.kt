package com.adastra.focuswallpaper

object CategoryOrder {
    fun move(categories: List<TaskCategory>, selectedId: String, direction: Int): List<TaskCategory> {
        if (direction != -1 && direction != 1) return categories
        val index = categories.indexOfFirst { it.id == selectedId }
        val target = index + direction
        if (index < 0 || target !in categories.indices) return categories
        return categories.toMutableList().apply {
            val neighbour = this[target]
            this[target] = this[index]
            this[index] = neighbour
        }
    }
}
