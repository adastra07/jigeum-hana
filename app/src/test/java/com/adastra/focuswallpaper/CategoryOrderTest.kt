package com.adastra.focuswallpaper

import org.junit.Assert.*
import org.junit.Test

class CategoryOrderTest {
    private val a = TaskCategory(DEFAULT_CATEGORY, "오늘")
    private val b = TaskCategory("study", "공부")
    private val c = TaskCategory("saved", "보관")
    private val categories = listOf(a, b, c)
    @Test fun repeatedMovesKeepSelectedIdentityAndSwapOnlyNeighbours() {
        val first = CategoryOrder.move(categories, a.id, 1)
        assertEquals(listOf(b, a, c), first)
        val second = CategoryOrder.move(first, a.id, 1)
        assertEquals(listOf(b, c, a), second)
        assertEquals(listOf(b, a, c), CategoryOrder.move(second, a.id, -1))
        assertEquals(categories, CategoryOrder.move(CategoryOrder.move(second, a.id, -1), a.id, -1))
        assertSame(a, second.last())
    }
    @Test fun boundariesUnknownIdsAndUnsupportedDirectionsDoNotChangeAnything() {
        assertEquals(categories, CategoryOrder.move(categories, a.id, -1))
        assertEquals(categories, CategoryOrder.move(categories, c.id, 1))
        assertEquals(categories, CategoryOrder.move(categories, "missing", 1))
        assertEquals(categories, CategoryOrder.move(categories, b.id, 2))
        assertEquals(listOf(a), CategoryOrder.move(listOf(a), a.id, 1))
    }
}
