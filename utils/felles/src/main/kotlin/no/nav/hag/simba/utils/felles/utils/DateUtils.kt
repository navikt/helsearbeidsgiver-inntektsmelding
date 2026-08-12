package no.nav.hag.simba.utils.felles.utils

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val norskDatoFormatKort = DateTimeFormatter.ofPattern("dd.MM.yy")

fun LocalDate.toYearMonth(): YearMonth = YearMonth.of(year, month)

fun YearMonth.toLocalDate(day: Int): LocalDate = LocalDate.of(year, monthValue, day)

fun LocalDate.tilNorskFormatKort(): String = format(norskDatoFormatKort)
