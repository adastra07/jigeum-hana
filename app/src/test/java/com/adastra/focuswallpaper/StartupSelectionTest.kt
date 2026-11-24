package com.adastra.focuswallpaper

import org.junit.Assert.*
import org.junit.Test

class StartupSelectionTest {
    @Test fun defaultFirstAndPreviousFollowStableIdsAndSavedOrder() {
        val ids=listOf("study",DEFAULT_CATEGORY,"home")
        assertEquals(DEFAULT_CATEGORY,StartupSelection.resolve("default",DEFAULT_CATEGORY,ids,"home"))
        assertEquals("study",StartupSelection.resolve("first",DEFAULT_CATEGORY,ids,"home"))
        assertEquals("home",StartupSelection.resolve("last",DEFAULT_CATEGORY,ids,"home"))
    }
    @Test fun calendarFirstIsAllAndHiddenOrRemovedSelectionsFallBackToAll() {
        val ids=listOf("",NOTE_UNCATEGORIZED,"study")
        assertEquals(NOTE_UNCATEGORIZED,StartupSelection.resolve("default",NOTE_UNCATEGORIZED,ids,"study"))
        assertEquals("",StartupSelection.resolve("first",NOTE_UNCATEGORIZED,ids,"study"))
        assertEquals("study",StartupSelection.resolve("last",NOTE_UNCATEGORIZED,ids,"study"))
        val hidden=listOf("","study")
        assertEquals("",StartupSelection.resolve("default",NOTE_UNCATEGORIZED,hidden,"study"))
        assertEquals("",StartupSelection.resolve("last",NOTE_UNCATEGORIZED,hidden,NOTE_UNCATEGORIZED))
        assertEquals("",StartupSelection.resolve("last",NOTE_UNCATEGORIZED,ids,"deleted"))
    }
}
