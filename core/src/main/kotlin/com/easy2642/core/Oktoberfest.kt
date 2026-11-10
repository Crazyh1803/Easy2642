package com.easy2642.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Dates of the Munich Oktoberfest, used for the once-a-year tip reminder. */
object Oktoberfest {
    /** Opening day: the first Saturday on or after 16 September. */
    fun start(year: Int): LocalDate =
        LocalDate.of(year, 9, 16).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))

    /** Last day: the first Sunday in October. */
    fun end(year: Int): LocalDate =
        LocalDate.of(year, 10, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY))

    fun isOn(date: LocalDate): Boolean = !date.isBefore(start(date.year)) && !date.isAfter(end(date.year))

    /** True at most once per calendar year, and only while the festival is on. */
    fun shouldPrompt(today: LocalDate, lastPromptYear: Int): Boolean = isOn(today) && lastPromptYear != today.year
}
