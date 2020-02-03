package org.sgdan.scheduler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeTest {
    private fun zdt(day: String, hour: String) = ZonedDateTime.parse("2019-10-${day}T${hour}:00Z")

    @Test
    fun schedules() {
        val thu8am = zdt("24", "08")
        val thu9am = zdt("24", "09")
        val fri8am = zdt("25", "08")
        val fri9am = zdt("25", "09")
        val sun10am = zdt("27", "10")
        val mon8am = zdt("28", "08")

        assertEquals(thu8am, lastScheduled(8, thu9am))
        assertEquals(thu9am, lastScheduled(9, fri8am))
        assertEquals(fri9am, lastScheduled(9, sun10am))
        assertEquals(fri9am, lastScheduled(9, mon8am))
    }

    @Test
    fun autoStart() {
        val wed8pm = ZonedDateTime.parse("2019-11-13T20:00Z")
        val wedAfter8pm = ZonedDateTime.parse("2019-11-13T20:32Z")
        val started = mostRecent(wed8pm, toZDT(0, ZoneId.systemDefault()))
        assertEquals("2019-11-13T20:00Z", "$started")
        assertEquals("2019-11-13T20:00Z", "${lastScheduled(20, wedAfter8pm)}")
        assertTrue(hoursFrom(started, wedAfter8pm) < 8)
    }

    @Test
    fun calcRemaining() {
        val window = 10
        val m = 60 * 1000
        val start = 1573261444114
        val stop = start + window * 60 * m // 8 hrs after start
        assertEquals("", remaining(0, stop, window))
        assertEquals("", remaining(stop - m + 1, start, window))
        assertEquals("1m", remaining(start, stop - m, window))
        assertEquals("5m", remaining(start, stop - 5 * m, window))
        assertEquals("10m", remaining(start, stop - 10 * m, window))
        assertEquals("1h 03m", remaining(start, stop - 63 * m, window))
        assertEquals("9h 59m", remaining(start, start + m, window))
        assertEquals("9h 59m", remaining(start, start + 1, window))
        assertEquals("", remaining(start, start, window))
        assertEquals("", remaining(start, start - 20 * m, window))
    }
}
