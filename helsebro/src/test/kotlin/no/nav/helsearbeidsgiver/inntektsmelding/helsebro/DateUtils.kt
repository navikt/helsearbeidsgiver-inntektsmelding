package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

private val defaultAar = 2018

val Int.januar get() =
    this.januar(defaultAar)

fun Int.januar(aar: Int): LocalDate =
    LocalDate.of(aar, Month.JANUARY, this)

fun januar(aar: Int): YearMonth =
    YearMonth.of(aar, Month.JANUARY)

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
