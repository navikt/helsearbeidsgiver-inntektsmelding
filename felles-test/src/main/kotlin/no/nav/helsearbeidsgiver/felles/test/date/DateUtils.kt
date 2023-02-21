package no.nav.helsearbeidsgiver.felles.test.date

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.YearMonth

// 01.01.2018 er en mandag
private val defaultAar = 2018

val Int.januar
    get(): LocalDate =
        januar(defaultAar)

fun Int.januar(aar: Int): LocalDate =
    LocalDate.of(aar, Month.JANUARY, this)

fun januar(aar: Int): YearMonth =
    YearMonth.of(aar, Month.JANUARY)

fun Int.februar(aar: Int): LocalDate =
    LocalDate.of(aar, Month.FEBRUARY, this)

val Int.februar
    get(): LocalDate =
        februar(defaultAar)

fun Int.mars(aar: Int): LocalDate =
    LocalDate.of(aar, Month.MARCH, this)

fun mars(aar: Int): YearMonth =
    YearMonth.of(aar, Month.MARCH)

val Int.oktober get() =
    this.oktober(defaultAar)

fun Int.oktober(aar: Int): LocalDate =
    LocalDate.of(aar, Month.OCTOBER, this)

fun oktober(aar: Int): YearMonth =
    YearMonth.of(aar, Month.OCTOBER)

val Int.november get() =
    this.november(defaultAar)

fun Int.november(aar: Int): LocalDate =
    LocalDate.of(aar, Month.NOVEMBER, this)

fun november(aar: Int): YearMonth =
    YearMonth.of(aar, Month.NOVEMBER)

val Int.desember get() =
    this.desember(defaultAar)

fun Int.desember(aar: Int): LocalDate =
    LocalDate.of(aar, Month.DECEMBER, this)

fun desember(aar: Int): YearMonth =
    YearMonth.of(aar, Month.DECEMBER)

fun LocalDate.kl(time: Int, minutt: Int, sekund: Int, nanosekund: Int): LocalDateTime =
    atTime(time, minutt, sekund, nanosekund)
