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
    @Test fun completingAppendsToBottomAndUncheckingReturnsToSavedPositionsEvenAfterReload() {
        val original=listOf(Task("a","A"),Task("b","B"),Task("c","C"),Task("d","D"))
        val first=TaskOrder.setCompleted(original,"c",true,20)
        val second=TaskCodec.decode(TaskCodec.encode(TaskOrder.setCompleted(first,"b",true,10)))
        assertEquals(listOf("a","d","c","b"),TaskOrder.visible(second,DEFAULT_CATEGORY,true).map { it.id })
        assertEquals(original.map { it.id },second.map { it.id })
        val unchecked=TaskOrder.setCompleted(second,"c",false,30)
        assertEquals(listOf("a","c","d","b"),TaskOrder.visible(unchecked,DEFAULT_CATEGORY,true).map { it.id })
        assertEquals(original,TaskOrder.setCompleted(unchecked,"b",false,40))
        assertFalse(original.any { it.done })
    }
    @Test fun disablingCompletionGroupingUsesOriginalOrderAndLeavesOtherListsAndTrashAlone() {
        val tasks=listOf(Task("a","A"),Task("b","B",categoryId="other"),Task("c","C"),Task("trash","삭제",deletedAt=1))
        val checked=TaskOrder.setCompleted(tasks,"a",true,10)
        assertEquals(listOf("c","a"),TaskOrder.visible(checked,DEFAULT_CATEGORY,true).map { it.id })
        assertEquals(listOf("a","c"),TaskOrder.visible(checked,DEFAULT_CATEGORY,false).map { it.id })
        assertEquals(tasks[1],checked[1]);assertEquals(tasks[3],checked[3])
        assertEquals(checked,TaskOrder.setCompleted(checked,"trash",true,100))
    }
    @Test fun groupedDragRetainsCheckedReturnSlotsAndCurrentTaskRecords() {
        val tasks=listOf(Task("a","A",stopwatch=true,stopwatchMs=123),Task("b","B",done=true,completedAt=10),
            Task("c","C"),Task("other","Other",categoryId="other"),Task("d","D",done=true,completedAt=20))
        val reordered=TaskOrder.reorder(tasks,DEFAULT_CATEGORY,listOf("c","a","d","b"),true)
        assertEquals(listOf("c","b","a","other","d"),TaskOrder.setCompleted(TaskOrder.setCompleted(reordered,"b",false,30),"d",false,40).map { it.id })
        assertEquals(listOf("c","a","d","b"),TaskOrder.visible(reordered,DEFAULT_CATEGORY,true).map { it.id })
        assertEquals(123L,reordered.first { it.id=="a" }.stopwatchMs);assertSame(tasks[3],reordered[3])
    }

}
