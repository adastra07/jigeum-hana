package com.adastra.focuswallpaper

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarLayoutTest {
    @Test fun rowsStayCompactAndFitOrdinaryViewport() {
        assertEquals(48, CalendarLayout.rowHeight(300))
        assertEquals(60, CalendarLayout.rowHeight(500))
        assertEquals(72, CalendarLayout.rowHeight(800))
        assertTrue(CalendarLayout.rowHeight(500) * 6 + 136 <= 500)
    }
    @Test fun movingMonthKeepsSelectionInsideMonthAcrossLeapYears() {
        assertEquals(LocalDate.of(2027, 2, 28), CalendarLayout.dateInMonth(LocalDate.of(2027, 1, 31), YearMonth.of(2027, 2)))
        assertEquals(LocalDate.of(2028, 2, 29), CalendarLayout.dateInMonth(LocalDate.of(2028, 1, 31), YearMonth.of(2028, 2)))
        assertEquals(LocalDate.of(2027, 1, 20), CalendarLayout.dateInMonth(LocalDate.of(2026, 12, 20), YearMonth.of(2027, 1)))
    }
}
