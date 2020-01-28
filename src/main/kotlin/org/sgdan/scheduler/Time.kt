package org.sgdan.scheduler

import java.time.DayOfWeek
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.Instant.ofEpochMilli
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.ZonedDateTime.ofInstant
import java.time.temporal.ChronoUnit
import kotlin.math.max

const val WINDOW = 8 // Eight hour up-time window
const val WINDOW_SECONDS = WINDOW * 60 * 60

private fun isWeekend(day: DayOfWeek) = setOf(SATURDAY, SUNDAY).contains(day)

/**
 * @return same time on most recent weekday
 */
private fun weekday(now: ZonedDateTime): ZonedDateTime =
        if (isWeekend(now.dayOfWeek)) weekday(now.minusDays(1)) else now

fun hoursFrom(earlier: ZonedDateTime, later: ZonedDateTime) =
        ChronoUnit.HOURS.between(earlier, later)

fun mostRecent(a: ZonedDateTime?, b: ZonedDateTime) = when {
    a == null -> b
    a.isAfter(b) -> a
    else -> b
}

fun toZDT(millis: Long, zoneId: ZoneId): ZonedDateTime =
        ofInstant(ofEpochMilli(millis), zoneId)

/**
 * @return same time as "now" but from most recent weekday, or null
 *         if no start hour has been specified
 */
fun lastScheduled(startHour: Int?, now: ZonedDateTime): ZonedDateTime {
    val last = startHour?.let {
        val start = weekday(now.withHour(it))
        if (start.isAfter(now)) weekday(start.minusDays(1)) else start
    }
    return last?.withMinute(0)?.withSecond(0) ?: toZDT(0, now.zone)
}

fun lastScheduledMillis(startHour: Int?, now: Long, zoneId: ZoneId): Long =
        lastScheduled(startHour, toZDT(now, zoneId)).toEpochSecond() * 1000

/**
 * Can only extend if there's less than 7 hours remaining
 */
fun canExtend(lastStarted: Long, now: Long) =
        remainingSeconds(lastStarted, now) < WINDOW_SECONDS - 60 * 60

/**
 * @return the last start time corresponding to a one hour extension
 */
fun extend(lastStarted: Long, now: Long): Long {
    val earliest = now - WINDOW_SECONDS * 1000
    val started = max(lastStarted, earliest)
    val hourLater = started + 60 * 60 * 1000
    return if (hourLater >= now) started else hourLater
}

fun remaining(lastStarted: Long, now: Long) =
        remainingTime(remainingSeconds(lastStarted, now))

private fun remainingSeconds(lastStarted: Long, now: Long) =
        max(lastStarted + WINDOW_SECONDS * 1000 - now, 0) / 1000

private fun remainingTime(remaining: Long): String {
    val m = remaining / 60
    val h = (m / 60) % WINDOW

    return when {
        m <= 0 || m >= WINDOW * 60 -> ""
        h > 0 -> "${h}h %02dm".format(m % 60)
        else -> "${m % 60}m"
    }
}
