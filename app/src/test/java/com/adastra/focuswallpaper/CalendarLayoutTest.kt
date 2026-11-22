package com.adastra.focuswallpaper

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarLayoutTest {
    @Test fun monthSwipeRequiresClearHorizontalMovement() {
        assertEquals(1, CalendarLayout.swipeDirection(-100f, 8f, 360, 1f))
        assertEquals(-1, CalendarLayout.swipeDirection(100f, -8f, 360, 1f))
        assertEquals(0, CalendarLayout.swipeDirection(20f, 0f, 360, 1f))
        assertEquals(0, CalendarLayout.swipeDirection(100f, 160f, 360, 1f))
        assertEquals(0, CalendarLayout.swipeDirection(80f, 0f, 720, 2f))
    }
    @Test fun rowsStayCompactAndFitOrdinaryViewport() {
        assertEquals(48, CalendarLayout.rowHeight(300))
        assertEquals(64, CalendarLayout.rowHeight(500))
        assertEquals(72, CalendarLayout.rowHeight(800))
        assertTrue(CalendarLayout.rowHeight(500) * 6 + 112 <= 500)
    }
    @Test fun movingMonthKeepsSelectionInsideMonthAcrossLeapYears() {
        assertEquals(LocalDate.of(2027, 2, 28), CalendarLayout.dateInMonth(LocalDate.of(2027, 1, 31), YearMonth.of(2027, 2)))
        assertEquals(LocalDate.of(2028, 2, 29), CalendarLayout.dateInMonth(LocalDate.of(2028, 1, 31), YearMonth.of(2028, 2)))
        assertEquals(LocalDate.of(2027, 1, 20), CalendarLayout.dateInMonth(LocalDate.of(2026, 12, 20), YearMonth.of(2027, 1)))
    }
}
