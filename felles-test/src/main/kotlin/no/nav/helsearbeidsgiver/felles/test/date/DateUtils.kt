package no.nav.helsearbeidsgiver.felles.test.date

import java.time.LocalDate
import java.time.Month

// 01.01.2018 er en mandag
private val defaultAar = 2018

val Int.januar
    get() =
        januar(defaultAar)

fun Int.januar(aar: Int): LocalDate =
    LocalDate.of(aar, Month.JANUARY, this)
