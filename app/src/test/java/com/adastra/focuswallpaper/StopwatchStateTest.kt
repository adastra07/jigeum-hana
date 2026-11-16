package com.adastra.focuswallpaper

import org.junit.Assert.*
import org.junit.Test

class StopwatchStateTest {
    private fun running() = TimerState("running", title = "수학", taskId = "a", mode = "stopwatch",
        elapsedMs = 30000, startedAt = 100000, startedRealtime = 1000, bootCount = 7)
    @Test fun runningWatchRecoversAfterProcessRecreationAndIgnoresClockChange() {
        assertEquals(35000L, running().elapsed(105000, 6000, 7))
        assertEquals(35000L, running().elapsed(900000, 6000, 7))
    }
    @Test fun pauseAndResumePreservePreviouslyAccumulatedTime() {
        val paused = running().pause(105000, 6000, 7)
        assertEquals(35000L, paused.elapsed(500000, 90000, 7))
        val resumed = paused.resume(500000, 90000, 7)
        assertEquals(37000L, resumed.elapsed(502000, 92000, 7))
        assertEquals("a", resumed.taskId)
    }
    @Test fun aWatchPausedAtZeroCanResume() {
        val paused = TimerState("paused", mode = "stopwatch", elapsedMs = 0)
        assertEquals("running", paused.resume(100, 50, 1).status)
    }
    @Test fun restartResetsOnlyTheWatchAndStartsImmediately() {
        val reset = running().pause(105000, 6000, 7).restart(200000, 10000, 7)
        assertEquals("running", reset.status)
        assertEquals(0L, reset.elapsed(200000, 10000, 7))
        assertEquals(2000L, reset.elapsed(202000, 12000, 7))
        assertEquals("a", reset.taskId)
        val timer = TimerState("running", endAt = 300000)
        assertEquals(timer, timer.restart(200000))
    }
    @Test fun rebootFallsBackToSavedWallTime() {
        assertEquals(90000L, running().elapsed(160000, 100, 8))
    }
    @Test fun differentTasksKeepIndependentSavedCountersAcrossStorage() {
        val a = Task("a", "수학", stopwatch = true, stopwatchMs = 42000)
        val b = Task("b", "독서", stopwatch = true, stopwatchMs = 15000)
        val stored = TaskCodec.decode(TaskCodec.encode(listOf(a.trash(100), b)))
        assertEquals(a, stored[0].restore())
        assertEquals(b, stored[1])
        assertTrue(stored[0].hasClock)
        assertFalse(stored[0].hasTimer)
        assertEquals(42000L, stored[0].copy(stopwatch = false).stopwatchMs)
    }
}
