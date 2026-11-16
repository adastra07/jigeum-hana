package com.adastra.focuswallpaper

object WallpaperResetPolicy {
    fun shouldClear(state: Int, hadFocus: Boolean, automaticEnabled: Boolean, hasNewSelection: Boolean): Boolean {
        if (hasNewSelection) return false
        return state == 1 || (state == -1 && hadFocus && automaticEnabled)
    }
}
