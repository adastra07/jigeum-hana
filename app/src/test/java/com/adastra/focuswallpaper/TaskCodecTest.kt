package com.adastra.focuswallpaper

import org.junit.Assert.*
import org.junit.Test

class TaskCodecTest {
    @Test fun legacyChecklistMigratesWithoutLosingFields() {
        val old = """[{"id":"a","text":"기존 할 일","done":true,"minutes":60}]"""
        val task = TaskCodec.decode(old).single()
        assertEquals("a", task.id)
        assertEquals("기존 할 일", task.text)
        assertTrue(task.done)
        assertEquals(60, task.minutes)
        assertEquals(DEFAULT_CATEGORY, task.categoryId)
        assertEquals(0L, task.deletedAt)
    }
    @Test fun trashSurvivesStorageAndRestoreKeepsTaskAndCategory() {
        val source = Task("a", "공부", true, 15, "school")
        val restored = TaskCodec.decode(TaskCodec.encode(listOf(source.trash(1234)))).single()
        assertEquals(1234L, restored.deletedAt)
        assertEquals(source, restored.restore())
    }
    @Test fun editingOneCategoryDoesNotDropOtherCategoriesOrTrash() {
        val first = Task("a", "공부", minutes = 10, categoryId = "school")
        val second = Task("b", "집안일", categoryId = "home").trash(100)
        val list = TaskCodec.decode(TaskCodec.encode(listOf(first, second)))
        list[0] = list[0].copy(text = "수학")
        val reloaded = TaskCodec.decode(TaskCodec.encode(list))
        assertEquals("수학", reloaded[0].text)
        assertEquals(second, reloaded[1])
    }
}
