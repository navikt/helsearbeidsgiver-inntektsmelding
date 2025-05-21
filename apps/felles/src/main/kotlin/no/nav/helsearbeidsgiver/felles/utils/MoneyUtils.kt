package no.nav.helsearbeidsgiver.felles.utils

import java.math.RoundingMode
import java.time.YearMonth

fun Map<YearMonth, Double?>.gjennomsnitt(): Double =
    if (isEmpty()) {
        0.0
    } else {
        values
            .mapNotNull { it }
            .sumMoney()
            .divideMoney(this.size)
    }

/** Forhindrer avrundingsfeil. */
fun List<Double>.sumMoney(): Double =
    sumOf(Double::toBigDecimal)
        .toDouble()

/** Forhindrer avrundingsfeil. */
fun Double.divideMoney(divisor: Int): Double =
    toBigDecimal()
        .divide(divisor.toBigDecimal(), 2, RoundingMode.HALF_UP)
        .toDouble()
