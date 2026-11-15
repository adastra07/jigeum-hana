package com.adastra.focuswallpaper

import org.junit.Assert.*
import org.junit.Test

class TimerStateTest {
    @Test fun runningTimerUsesDeadlineAfterProcessRecreation() {
        val restored = TimerState("running", endAt = 150000, title = "공부", taskId = "a")
        assertEquals(90000L, restored.remaining(60000))
        assertEquals(0L, restored.remaining(160000))
    }
    @Test fun pauseFreezesAndResumeMovesDeadline() {
        val running = TimerState("running", endAt = 150000, title = "공부", taskId = "a")
        val paused = running.pause(60000)
        assertEquals(90000L, paused.remaining(999999))
        val resumed = paused.resume(200000)
        assertEquals(290000L, resumed.endAt)
        assertEquals("a", resumed.taskId)
        assertEquals(60000L, resumed.remaining(230000))
    }
    @Test fun inactiveTransitionsAreNoOpsAndExpiryCannotResume() {
        assertEquals(TimerState(), TimerState().pause(1).resume(2))
        val expired = TimerState("running", endAt = 10).pause(20)
        assertEquals(0L, expired.pausedMs)
        assertEquals(expired, expired.resume(30))
    }
}
