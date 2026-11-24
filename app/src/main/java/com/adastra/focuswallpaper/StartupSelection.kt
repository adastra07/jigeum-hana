package com.adastra.focuswallpaper

object StartupSelection {
    const val DEFAULT = "default"
    const val FIRST = "first"
    const val LAST = "last"
    val modes = listOf(DEFAULT, FIRST, LAST)
    fun resolve(mode: String, defaultId: String, visibleIds: List<String>, previousId: String): String {
        require(visibleIds.isNotEmpty())
        return when (mode) {
            DEFAULT -> defaultId.takeIf { it in visibleIds } ?: visibleIds.first()
            LAST -> previousId.takeIf { it in visibleIds } ?: visibleIds.first()
            else -> visibleIds.first()
        }
    }
}
