package no.nav.helsearbeidsgiver.felles.utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val norskDatoFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val zoneIdOslo: ZoneId = ZoneId.of("Europe/Oslo")

fun LocalDate.tilNorskFormat(): String = format(norskDatoFormat)

fun LocalDate.toYearMonth(): YearMonth = YearMonth.of(year, month)

fun LocalDateTime.toOffsetDateTimeOslo(): OffsetDateTime = atZone(zoneIdOslo).toOffsetDateTime()

fun YearMonth.toLocalDate(day: Int): LocalDate = LocalDate.of(year, monthValue, day)
