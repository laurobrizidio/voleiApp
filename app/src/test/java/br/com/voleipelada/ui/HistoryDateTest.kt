package br.com.voleipelada.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HistoryDateTest {
    private fun utc(day: Int, hour: Int = 0, minute: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, Calendar.NOVEMBER, day, hour, minute)
        }.timeInMillis

    @Test fun dayFilterUsesLocalMidnightAndDatePickerUsesUtc() {
        val manaus = TimeZone.getTimeZone("America/Manaus")
        assertEquals(utc(21), localDayAsUtc(utc(22, 3, 59), manaus))
        assertEquals(utc(22), localDayAsUtc(utc(22, 4), manaus))
    }
}
