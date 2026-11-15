package com.adastra.focuswallpaper

data class TimerState(
    val status: String = "idle", val endAt: Long = 0, val pausedMs: Long = 0,
    val title: String = "", val taskId: String = ""
) {
    fun remaining(now: Long): Long = (if (status == "running") endAt - now else pausedMs).coerceAtLeast(0)
    fun pause(now: Long) = if (status == "running") copy(status = "paused", endAt = 0, pausedMs = remaining(now)) else this
    fun resume(now: Long) = if (status == "paused" && pausedMs > 0) copy(status = "running", endAt = now + pausedMs, pausedMs = 0) else this
}
