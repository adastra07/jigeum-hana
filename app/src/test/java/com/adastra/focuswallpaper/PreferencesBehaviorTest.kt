package com.adastra.focuswallpaper

import org.junit.Assert.*
import org.junit.Test

class PreferencesBehaviorTest {
    @Test fun emptyingTrashPreservesEveryActiveCategoryAndCompletionState() {
        val school = Task("a", "수학", true, 15, "school")
        val home = Task("b", "정리", false, 60, "home")
        val deleted = school.copy(id = "c").trash(100)
        assertEquals(listOf(school, home), TrashOperations.empty(listOf(school, deleted, home)))
        assertEquals(emptyList<Task>(), TrashOperations.empty(listOf(deleted)))
    }
    @Test fun pastelBackgroundsUseDarkTextAndBlackUsesWhiteText() {
        WallpaperPalette.colors.filter { it.first != "검정" }.forEach {
            assertEquals(0xFF27382F.toInt(), WallpaperPalette.textColor(it.second))
        }
        assertEquals(0xFFFFFFFF.toInt(), WallpaperPalette.textColor(0xFF000000.toInt()))
    }
}
