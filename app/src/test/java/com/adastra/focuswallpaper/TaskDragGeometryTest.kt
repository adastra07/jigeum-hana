package com.adastra.focuswallpaper

import org.junit.Assert.*
import org.junit.Test

class TaskDragGeometryTest {
    private val slots = listOf(TaskDragGeometry.Slot(0, 48), TaskDragGeometry.Slot(48, 96), TaskDragGeometry.Slot(96, 144))
    @Test fun downwardMoveNeedsOnlyHalfTheDraggedRowInNextSlot() {
        assertEquals(0, TaskDragGeometry.destination(0, 23f, 48, slots))
        assertEquals(1, TaskDragGeometry.destination(0, 25f, 48, slots))
    }
    @Test fun upwardMoveUsesTheSameHalfRowThreshold() {
        assertEquals(1, TaskDragGeometry.destination(1, 25f, 48, slots))
        assertEquals(0, TaskDragGeometry.destination(1, 23f, 48, slots))
    }
    @Test fun sharedEdgeDoesNotOscillateAfterSwap() {
        assertEquals(0, TaskDragGeometry.destination(0, 24f, 48, slots))
        assertEquals(1, TaskDragGeometry.destination(1, 24f, 48, slots))
        assertEquals(1, TaskDragGeometry.destination(1, 25f, 48, slots))
    }
    @Test fun centreAlsoWorksForMultilineRowsAndFastMoves() {
        val mixed = listOf(TaskDragGeometry.Slot(0, 96), TaskDragGeometry.Slot(96, 144), TaskDragGeometry.Slot(144, 240))
        assertEquals(0, TaskDragGeometry.destination(0, 47f, 96, mixed))
        assertEquals(1, TaskDragGeometry.destination(0, 49f, 96, mixed))
        assertEquals(2, TaskDragGeometry.destination(0, 150f, 96, mixed))
        assertEquals(0, TaskDragGeometry.destination(2, -100f, 96, mixed))
    }
}
