package dev.stade.ui.components

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun formatChatTime(epochMillis: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val nowEpochMs = Clock.System.now().toEpochMilliseconds()
    val msg = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(tz)
    val now = kotlinx.datetime.Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(tz)

    val hh = msg.hour.toString().padStart(2, '0')
    val mm = msg.minute.toString().padStart(2, '0')
    val timeOnly = "$hh:$mm"

    val sameDay = msg.year == now.year && msg.dayOfYear == now.dayOfYear
    if (sameDay) return timeOnly

    val msgDate = LocalDateTime(msg.year, msg.monthNumber, msg.dayOfMonth, 0, 0)
    val nowDate = LocalDateTime(now.year, now.monthNumber, now.dayOfMonth, 0, 0)
    val diffDays = epochDays(nowDate) - epochDays(msgDate)
    if (diffDays == 1L) return dev.stade.ui.i18n.I18n.current.timeYesterday(timeOnly)

    val dd = msg.dayOfMonth.toString().padStart(2, '0')
    val mo = msg.monthNumber.toString().padStart(2, '0')
    if (msg.year == now.year) return "$dd.$mo $timeOnly"
    val yy = (msg.year % 100).toString().padStart(2, '0')
    return "$dd.$mo.$yy $timeOnly"
}

fun formatScheduledTime(epochMillis: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val target = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(tz)
    val now = Clock.System.now().toLocalDateTime(tz)
    val dd = target.dayOfMonth.toString().padStart(2, '0')
    val mo = target.monthNumber.toString().padStart(2, '0')
    val hh = target.hour.toString().padStart(2, '0')
    val mm = target.minute.toString().padStart(2, '0')
    if (target.year != now.year) {
        val yy = (target.year % 100).toString().padStart(2, '0')
        return "$dd.$mo.$yy $hh:$mm"
    }
    return "$dd.$mo $hh:$mm"
}

fun formatVanishRemaining(remainingMs: Long): String {
    val totalMinutes = remainingMs.coerceAtLeast(0L) / 60_000L
    return when {
        totalMinutes >= 60 && totalMinutes % 60 > 0 -> "${totalMinutes / 60}h ${totalMinutes % 60}m"
        totalMinutes >= 60 -> "${totalMinutes / 60}h"
        else -> "${totalMinutes}m"
    }
}

fun formatVoiceDuration(ms: Int): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

private fun epochDays(d: LocalDateTime): Long {
    val y = d.year.toLong()
    val m = d.monthNumber.toLong()
    val day = d.dayOfMonth.toLong()
    val a = (14 - m) / 12
    val y2 = y + 4800 - a
    val m2 = m + 12 * a - 3
    return day + (153 * m2 + 2) / 5 + 365 * y2 + y2 / 4 - y2 / 100 + y2 / 400 - 32045
}


fun formatExactTimestamp(epochMillis: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val at = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(tz)
    val month = at.monthNumber.toString().padStart(2, '0')
    val day = at.dayOfMonth.toString().padStart(2, '0')
    val hh = at.hour.toString().padStart(2, '0')
    val mm = at.minute.toString().padStart(2, '0')
    val ss = at.second.toString().padStart(2, '0')
    return "${at.year}-$month-$day  $hh:$mm:$ss"
}


fun formatExactDate(epochMillis: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val at = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(tz)
    val month = at.monthNumber.toString().padStart(2, '0')
    val day = at.dayOfMonth.toString().padStart(2, '0')
    return "${at.year}-$month-$day"
}
