package no.nav.helsearbeidsgiver.felles.test.date

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.YearMonth

// 01.01.2018 er en mandag
private val defaultAar = 2018

val Int.januar get(): LocalDate =
    januar(defaultAar)

fun Int.januar(aar: Int): LocalDate =
    LocalDate.of(aar, Month.JANUARY, this)

fun januar(aar: Int): YearMonth =
    1.januar(aar).toYearMonth()

val Int.februar get(): LocalDate =
    februar(defaultAar)

fun Int.februar(aar: Int): LocalDate =
    LocalDate.of(aar, Month.FEBRUARY, this)

fun februar(aar: Int): YearMonth =
    1.februar(aar).toYearMonth()

val Int.mars get(): LocalDate =
    mars(defaultAar)

fun Int.mars(aar: Int): LocalDate =
    LocalDate.of(aar, Month.MARCH, this)

fun mars(aar: Int): YearMonth =
    1.mars(aar).toYearMonth()

val Int.april get(): LocalDate =
    april(defaultAar)

fun Int.april(aar: Int): LocalDate =
    LocalDate.of(aar, Month.APRIL, this)

fun april(aar: Int): YearMonth =
    1.april(aar).toYearMonth()

val Int.mai get(): LocalDate =
    mai(defaultAar)

fun Int.mai(aar: Int): LocalDate =
    LocalDate.of(aar, Month.MAY, this)

fun mai(aar: Int): YearMonth =
    1.mai(aar).toYearMonth()

val Int.juni get(): LocalDate =
    juni(defaultAar)

fun Int.juni(aar: Int): LocalDate =
    LocalDate.of(aar, Month.JUNE, this)

fun juni(aar: Int): YearMonth =
    1.juni(aar).toYearMonth()

val Int.juli get(): LocalDate =
    juli(defaultAar)

fun Int.juli(aar: Int): LocalDate =
    LocalDate.of(aar, Month.JULY, this)

fun juli(aar: Int): YearMonth =
    1.juli(aar).toYearMonth()

val Int.august get(): LocalDate =
    august(defaultAar)

fun Int.august(aar: Int): LocalDate =
    LocalDate.of(aar, Month.AUGUST, this)

fun august(aar: Int): YearMonth =
    1.august(aar).toYearMonth()

val Int.september get(): LocalDate =
    september(defaultAar)

fun Int.september(aar: Int): LocalDate =
    LocalDate.of(aar, Month.SEPTEMBER, this)

fun september(aar: Int): YearMonth =
    1.september(aar).toYearMonth()

val Int.oktober get() =
    oktober(defaultAar)

fun Int.oktober(aar: Int): LocalDate =
    LocalDate.of(aar, Month.OCTOBER, this)

fun oktober(aar: Int): YearMonth =
    1.oktober(aar).toYearMonth()

val Int.november get() =
    november(defaultAar)

fun Int.november(aar: Int): LocalDate =
    LocalDate.of(aar, Month.NOVEMBER, this)

fun november(aar: Int): YearMonth =
    1.november(aar).toYearMonth()

val Int.desember get() =
    desember(defaultAar)

fun Int.desember(aar: Int): LocalDate =
    LocalDate.of(aar, Month.DECEMBER, this)

fun desember(aar: Int): YearMonth =
    1.desember(aar).toYearMonth()

fun LocalDate.kl(time: Int, minutt: Int, sekund: Int, nanosekund: Int): LocalDateTime =
    atTime(time, minutt, sekund, nanosekund)

private fun LocalDate.toYearMonth(): YearMonth =
    YearMonth.from(this)
