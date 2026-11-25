package io.github.hiramrg.delphis.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Settings → Play stats: time played per day (all games together), kept as "yyyy-MM-dd" → ms for the last
 * [KEEP_DAYS] days. Counting by day starts with build 355; totals per game go back further.
 */
object PlayStats {
    const val KEEP_DAYS = 120L

    fun dayKey(date: LocalDate): String = date.toString()

    /** [days] plus [ms] played on [date], without days older than [KEEP_DAYS]. */
    fun add(days: Map<String, Long>, date: LocalDate, ms: Long): Map<String, Long> {
        val oldest = date.minusDays(KEEP_DAYS)
        val kept = days.filterKeys { k -> runCatching { !LocalDate.parse(k).isBefore(oldest) }.getOrDefault(false) }
        val key = dayKey(date)
        return kept + (key to (kept[key] ?: 0L) + ms)
    }

    data class Day(val date: LocalDate, val label: String, val ms: Long, val today: Boolean, val future: Boolean)

    /** Monday to Sunday of the week [today] is in. */
    fun week(days: Map<String, Long>, today: LocalDate, locale: Locale = Locale.getDefault()): List<Day> {
        val monday = today.with(DayOfWeek.MONDAY)
        return (0L until 7L).map { i ->
            val d = monday.plusDays(i)
            Day(d, d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), days[dayKey(d)] ?: 0L, d == today, d.isAfter(today))
        }
    }

    /** Total of the week before [today]'s week. */
    fun lastWeekTotal(days: Map<String, Long>, today: LocalDate): Long {
        val monday = today.with(DayOfWeek.MONDAY).minusDays(7)
        return (0L until 7L).sumOf { days[dayKey(monday.plusDays(it))] ?: 0L }
    }

    /** The first day anything was counted (null = nothing yet). */
    fun firstDay(days: Map<String, Long>): LocalDate? =
        days.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.minOrNull()

    /** "45 min", "3 h 40 min", "12 h" (whole hours from 10 h on), "0 min". */
    fun format(ms: Long): String {
        val min = ms / 60_000
        return when {
            min < 60 -> "$min min"
            min < 600 && min % 60 != 0L -> "${min / 60} h ${min % 60} min"
            else -> "${min / 60} h"
        }
    }

    /** "1 h 10 min more than last week" / "20 min less than last week" / "Same as last week". */
    fun compare(thisWeek: Long, lastWeek: Long): String {
        val diff = thisWeek - lastWeek
        return when {
            diff / 60_000 == 0L -> "Same as last week"
            diff > 0 -> "${format(diff)} more than last week"
            else -> "${format(-diff)} less than last week"
        }
    }

    data class GameStat(val key: String, val ms: Long, val sessions: Long)

    /** Games by time played, most first (only games with some time). */
    fun mostPlayed(time: Map<String, Long>, sessions: Map<String, Long>, limit: Int = 5): List<GameStat> =
        time.filterValues { it >= 60_000 }.entries.sortedByDescending { it.value }.take(limit)
            .map { GameStat(it.key, it.value, sessions[it.key] ?: 0L) }
}
