package com.adastra.focuswallpaper

import org.junit.Assert.*
import org.junit.Test

class TaskOrderTest {
    @Test fun reorderedCategoryKeepsOtherCategoriesAndTrashInPlace() {
        val tasks = listOf(Task("a", "A"), Task("other", "Other", categoryId = "home"),
            Task("b", "B", done = true), Task("trash", "Trash").trash(10), Task("c", "C"))
        val ordered = TaskOrder.reorder(tasks, DEFAULT_CATEGORY, listOf("c", "a", "b"))
        assertEquals(listOf("c", "other", "a", "trash", "b"), ordered.map { it.id })
        assertEquals(tasks[1], ordered[1]); assertEquals(tasks[3], ordered[3])
        assertEquals(ordered, TaskCodec.decode(TaskCodec.encode(ordered)))
    }
    @Test fun currentStopwatchRecordsAndFieldsArePreserved() {
        val tasks = listOf(Task("a", "Edited", stopwatch = true, stopwatchMs = 12345), Task("b", "B"))
        val ordered = TaskOrder.reorder(tasks, DEFAULT_CATEGORY, listOf("b", "a"))
        assertSame(tasks[0], ordered[1]); assertEquals(12345L, ordered[1].stopwatchMs)
        assertEquals(tasks, TaskOrder.reorder(ordered, DEFAULT_CATEGORY, listOf("a", "b")))
    }
    @Test fun staleOrDuplicateDragIdsNeverLoseTasks() {
        val tasks = listOf(Task("a", "A"), Task("b", "B"))
        listOf(listOf("a"), listOf("a", "a"), listOf("b", "missing"), emptyList()).forEach {
            assertEquals(tasks, TaskOrder.reorder(tasks, DEFAULT_CATEGORY, it))
        }
    }
}
