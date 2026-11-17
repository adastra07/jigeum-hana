package com.adastra.focuswallpaper

import org.junit.Assert.*
import org.junit.Test

class AppearancePreferencesTest {
    @Test fun nineFontStepsKeepDefaultExactlyInTheMiddle() {
        assertEquals(9, AppearancePreferences.fontSizes.size)
        assertEquals(AppearancePreferences.fontSizes.size / 2, AppearancePreferences.DEFAULT_FONT_STEP)
        assertEquals(16, AppearancePreferences.fontSizes[AppearancePreferences.DEFAULT_FONT_STEP])
        assertEquals(1f, AppearancePreferences.fontScale(AppearancePreferences.DEFAULT_FONT_STEP), 0f)
        assertEquals(.75f, AppearancePreferences.fontScale(-100), 0f)
        assertEquals(20f / 16, AppearancePreferences.fontScale(100), 0f)
        assertTrue((0..7).all { AppearancePreferences.fontScale(it) < AppearancePreferences.fontScale(it + 1) })
    }
    @Test fun unassignedAlwaysStartsWithoutAnyClock() {
        assertEquals(FocusDefaults.Value(0, false), FocusDefaults.resolve(FocusDefaults.UNASSIGNED, 30, true))
    }
    @Test fun stopwatchOverridesLastTimerOrUnassigned() {
        assertTrue(FocusDefaults.resolve(FocusDefaults.STOPWATCH, 0, false).stopwatch)
        assertTrue(FocusDefaults.resolve(FocusDefaults.STOPWATCH, 25, false).stopwatch)
    }
    @Test fun lastModeKeepsPreviousChoiceIncludingZeroAndStopwatch() {
        assertEquals(FocusDefaults.Value(0, false), FocusDefaults.resolve(FocusDefaults.LAST, 0, false))
        assertEquals(FocusDefaults.Value(30, false), FocusDefaults.resolve(FocusDefaults.LAST, 30, false))
        assertEquals(FocusDefaults.Value(25, true), FocusDefaults.resolve(FocusDefaults.LAST, 25, true))
        assertEquals(FocusDefaults.Value(25, true), FocusDefaults.resolve("old", 25, true))
    }
}
