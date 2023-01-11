package no.nav.helsearbeidsgiver.felles.test.date

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month

// 01.01.2018 er en mandag
private val defaultAar = 2018

val Int.januar
    get(): LocalDate =
        januar(defaultAar)

fun Int.januar(aar: Int): LocalDate =
    LocalDate.of(aar, Month.JANUARY, this)

fun LocalDate.kl(time: Int, minutt: Int, sekund: Int, nanosekund: Int): LocalDateTime =
    atTime(time, minutt, sekund, nanosekund)
