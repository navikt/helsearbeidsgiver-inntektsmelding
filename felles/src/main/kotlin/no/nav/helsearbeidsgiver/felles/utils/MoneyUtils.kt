package no.nav.helsearbeidsgiver.felles.utils

import java.math.RoundingMode

/** Forhindrer avrundingsfeil. */
fun List<Double>.sumMoney(): Double =
    sumOf(Double::toBigDecimal)
        .toDouble()

/** Forhindrer avrundingsfeil. */
fun Double.divideMoney(divisor: Int): Double =
    toBigDecimal()
        .divide(divisor.toBigDecimal(), 2, RoundingMode.HALF_UP)
        .toDouble()
