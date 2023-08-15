package no.nav.helsearbeidsgiver.felles.utils

import java.time.LocalDate
import java.time.YearMonth

fun LocalDate.toYearMonth(): YearMonth =
    YearMonth.of(year, month)

fun YearMonth.toLocalDate(day: Int): LocalDate =
    LocalDate.of(year, monthValue, day)
