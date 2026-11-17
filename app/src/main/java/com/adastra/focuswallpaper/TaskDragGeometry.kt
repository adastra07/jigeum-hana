package com.adastra.focuswallpaper

object TaskDragGeometry {
    data class Slot(val top: Int, val bottom: Int)

    // With equal-height rows, the centre enters the next slot once half the row overlaps it.
    // Strict boundaries avoid swapping back and forth when exactly on a shared edge.
    fun destination(current: Int, top: Float, height: Int, slots: List<Slot>): Int {
        if (height <= 0 || current !in slots.indices) return current
        val centre = top + height / 2f
        return slots.indices.firstOrNull { index ->
            index != current && centre > slots[index].top && centre < slots[index].bottom
        } ?: when {
            slots.isNotEmpty() && centre < slots.first().top -> 0
            slots.isNotEmpty() && centre > slots.last().bottom -> slots.lastIndex
            else -> current
        }
    }
}
