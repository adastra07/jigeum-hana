package com.adastra.focuswallpaper

object AppearancePreferences {
    const val DEFAULT_FONT_STEP = 4
    val fontSizes = (12..20).toList()
    fun fontScale(step: Int) = fontSizes[step.coerceIn(fontSizes.indices)].toFloat() / 16f
}

object FocusDefaults {
    const val LAST = "last"
    const val UNASSIGNED = "unassigned"
    const val STOPWATCH = "stopwatch"
    val modes = listOf(UNASSIGNED, STOPWATCH, LAST)
    data class Value(val minutes: Int, val stopwatch: Boolean)
    fun resolve(mode: String, lastMinutes: Int, lastStopwatch: Boolean): Value = when (mode) {
        UNASSIGNED -> Value(0, false)
        STOPWATCH -> Value(lastMinutes.coerceIn(0, 180), true)
        else -> Value(lastMinutes.coerceIn(0, 180), lastStopwatch)
    }
}
