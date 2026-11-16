package com.adastra.focuswallpaper

import org.junit.Assert.*
import org.junit.Test

class WallpaperResetPolicyTest {
    @Test fun neverAppliedOrAlreadyClearedBackgroundIsLeftUntouched() {
        assertFalse(WallpaperResetPolicy.shouldClear(0, true, true, false))
        assertFalse(WallpaperResetPolicy.shouldClear(-1, true, false, false))
        assertFalse(WallpaperResetPolicy.shouldClear(-1, false, true, false))
    }
    @Test fun appliedFocusTextCanBeClearedEvenAfterAutomaticChangesAreDisabled() {
        assertTrue(WallpaperResetPolicy.shouldClear(1, true, false, false))
        assertTrue(WallpaperResetPolicy.shouldClear(-1, true, true, false))
    }
    @Test fun anOlderClearRequestCannotWipeANewSelection() {
        assertFalse(WallpaperResetPolicy.shouldClear(1, true, true, true))
        assertFalse(WallpaperResetPolicy.shouldClear(-1, true, true, true))
    }
}
