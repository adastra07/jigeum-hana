package com.adastra.focuswallpaper

object WallpaperPalette {
    val colors = listOf(
        "민트" to 0xFFE3EDE5.toInt(), "크림" to 0xFFF4EEDB.toInt(),
        "하늘" to 0xFFE0EAF2.toInt(), "라벤더" to 0xFFE8E2F0.toInt(),
        "살구" to 0xFFF3E1D6.toInt(), "분홍" to 0xFFF1E0E5.toInt(),
        "연두" to 0xFFE8EED6.toInt(), "회청" to 0xFFDCE7E9.toInt(),
        "흰색" to 0xFFFFFFFF.toInt(), "검정" to 0xFF000000.toInt()
    )
    fun textColor(background: Int): Int {
        val r = (background ushr 16) and 255
        val g = (background ushr 8) and 255
        val b = background and 255
        return if (299 * r + 587 * g + 114 * b >= 160000) 0xFF27382F.toInt() else 0xFFFFFFFF.toInt()
    }
}

object TrashOperations {
    fun empty(tasks: List<Task>): List<Task> = tasks.filter { it.deletedAt == 0L }
}
