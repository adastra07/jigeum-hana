package com.adastra.focuswallpaper

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarNoteTest {
    private val day = LocalDate.of(2026, 11, 25)
    @Test fun gridStartsOnSundayAndIncludesAdjacentMonths() {
        val cells = CalendarDates.cells(YearMonth.of(2026, 11))
        assertEquals(42, cells.size); assertEquals(LocalDate.of(2026, 11, 1), cells.first())
        assertEquals(LocalDate.of(2026, 12, 12), cells.last())
        assertTrue(cells.zipWithNext().all { (a, b) -> a.plusDays(1) == b })
        assertEquals(LocalDate.of(2026, 7, 26), CalendarDates.gridStart(YearMonth.of(2026, 8)))
    }
    @Test fun leapDayAndYearBoundaryAreIncluded() {
        assertTrue(CalendarDates.cells(YearMonth.of(2028, 2)).contains(LocalDate.of(2028, 2, 29)))
        assertTrue(CalendarDates.cells(YearMonth.of(2026, 12)).any { it.year == 2027 })
    }
    @Test fun multiDayScheduleIncludesBothEndpointsOnly() {
        val schedule = NoteSchedule(day, day.plusDays(2))
        assertTrue(schedule.isValid()); assertTrue(schedule.contains(day)); assertTrue(schedule.contains(day.plusDays(2)))
        assertFalse(schedule.contains(day.minusDays(1))); assertFalse(schedule.contains(day.plusDays(3)))
        assertTrue(schedule.overlaps(day.plusDays(1), day.plusDays(4)))
        assertFalse(schedule.overlaps(day.minusDays(4), day.minusDays(1)))
    }
    @Test fun invalidTimesAndReversedDatesAreRejected() {
        assertFalse(NoteSchedule(day, day.minusDays(1)).isValid())
        assertFalse(NoteSchedule(day, day, 600, 599).isValid())
        assertFalse(NoteSchedule(day, day, -1, 600).isValid())
        assertFalse(NoteSchedule(day, day, 0, 1440).isValid())
        assertFalse(NoteSchedule(day, day, 600, null).isValid())
        assertTrue(NoteSchedule(day, day.plusDays(1), 1380, 60).isValid())
        assertTrue(NoteSchedule(day, day, 0, 0).isValid())
    }
    @Test fun multipleNotesOnOneDateKeepOwnIdentityAndCategoryFiltering() {
        val a = Note(id = "a", title = "공부", categoryId = "school", schedule = NoteSchedule(day))
        val b = Note(id = "b", title = "공부", categoryId = "home", schedule = NoteSchedule(day))
        val plain = Note(title = "나중의 일반 노트")
        val notes = listOf(a, b, a.copy(id = "trash", deletedAt = 1), plain)
        assertEquals(listOf(a, b), CalendarDates.entries(notes, day))
        assertEquals(listOf(a), CalendarDates.entries(notes, day, "school"))
        assertEquals(emptyList<Note>(), CalendarDates.entries(notes, day.plusDays(1)))
    }
    @Test fun allDayPrecedesTimedNotesAndOrderingIsStable() {
        val allDay = Note(id = "a", createdAt = 10, schedule = NoteSchedule(day))
        val morning = Note(id = "b", createdAt = 20, schedule = NoteSchedule(day, day, 540, 600))
        val evening = Note(id = "c", createdAt = 1, schedule = NoteSchedule(day, day, 1080, 1140))
        assertEquals(listOf(allDay, morning, evening), CalendarDates.entries(listOf(evening, allDay, morning), day))
    }
    @Test fun notePayloadRoundTripsUnicodeDatesTimesAndPlainText() {
        val note = Note(title = "여행\n둘째 날", body = "가나다 😀\n내용\t체크", categoryId = "trip",
            schedule = NoteSchedule(day, day.plusDays(2), 550, 960), createdAt = 123, updatedAt = 456)
        assertEquals(note, NoteCodec.decode(NoteCodec.encode(note)))
    }
    @Test fun futureOrdinaryNoteSharesPayloadWithoutScheduling() {
        val note = Note(title = "일반 메모", body = "날짜 정보 없음", schedule = null)
        assertEquals(note, NoteCodec.decode(NoteCodec.encode(note)))
        assertNull(NoteCodec.decode(NoteCodec.encode(note)).schedule)
    }
    @Test fun trashAndRestorePreserveTitleContentCategoryAndSchedule() {
        val note = Note(title = "약속", body = "준비물", categoryId = "friends", schedule = NoteSchedule(day), updatedAt = 100)
        val restored = NoteCodec.decode(NoteCodec.encode(note.trash(100))).restore()
        assertEquals(note, restored)
        assertEquals(1L, note.trash(0).deletedAt)
    }
    @Test fun emptyDraftAndUntitledBodyHaveDefinedDisplayBehavior() {
        assertFalse(Note(schedule = NoteSchedule(day)).hasContent)
        assertFalse(Note(title = "  ", body = "\n ").hasContent)
        assertTrue(Note(body = "내용").hasContent)
        assertEquals("첫 문장", Note(body = "\n첫 문장\n다음").displayTitle)
        assertEquals("제목 없음", Note().displayTitle)
    }
}
