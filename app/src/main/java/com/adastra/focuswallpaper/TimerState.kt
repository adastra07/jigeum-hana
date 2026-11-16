package com.adastra.focuswallpaper

data class TimerState(
    val status: String = "idle", val endAt: Long = 0, val pausedMs: Long = 0,
    val title: String = "", val taskId: String = "", val mode: String = "timer",
    val elapsedMs: Long = 0, val startedAt: Long = 0,
    val startedRealtime: Long = -1, val bootCount: Int = -1
) {
    val isStopwatch: Boolean get() = mode == "stopwatch"
    fun remaining(now: Long): Long = (if (status == "running") endAt - now else pausedMs).coerceAtLeast(0)
    fun elapsed(now: Long, realtime: Long = -1, currentBoot: Int = -1): Long {
        if (!isStopwatch) return 0
        val delta = if (status != "running") 0 else if (
            bootCount >= 0 && bootCount == currentBoot && startedRealtime >= 0 && realtime >= startedRealtime
        ) realtime - startedRealtime else (now - startedAt).coerceAtLeast(0)
        return elapsedMs.coerceAtLeast(0) + delta
    }
    fun pause(now: Long, realtime: Long = -1, currentBoot: Int = -1): TimerState {
        if (status != "running") return this
        return if (isStopwatch) copy(status = "paused", elapsedMs = elapsed(now, realtime, currentBoot), startedAt = 0, startedRealtime = -1)
            else copy(status = "paused", endAt = 0, pausedMs = remaining(now))
    }
    fun resume(now: Long, realtime: Long = -1, currentBoot: Int = -1): TimerState {
        if (status != "paused") return this
        return if (isStopwatch) copy(status = "running", startedAt = now, startedRealtime = realtime, bootCount = currentBoot)
            else if (pausedMs > 0) copy(status = "running", endAt = now + pausedMs, pausedMs = 0) else this
    }
    fun restart(now: Long, realtime: Long = -1, currentBoot: Int = -1): TimerState =
        if (isStopwatch && status in listOf("running", "paused")) copy(status = "running", elapsedMs = 0,
            startedAt = now, startedRealtime = realtime, bootCount = currentBoot) else this
}
