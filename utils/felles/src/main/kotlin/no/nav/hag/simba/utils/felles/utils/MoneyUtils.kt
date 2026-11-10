package no.nav.hag.simba.utils.felles.utils

import java.math.RoundingMode
import java.time.YearMonth

fun Map<YearMonth, Double?>.gjennomsnitt(): Double =
    if (isEmpty()) {
        0.0
    } else {
        val antallMaanederMedInntekt = values.filterNotNull().size
        if (antallMaanederMedInntekt == 0) {
            return 0.0
        }
        values
            .mapNotNull { it }
            .sumMoney()
            .divideMoney(antallMaanederMedInntekt)
    }

/** Forhindrer avrundingsfeil. */
internal fun List<Double>.sumMoney(): Double =
    sumOf(Double::toBigDecimal)
        .toDouble()

/** Forhindrer avrundingsfeil. */
internal fun Double.divideMoney(divisor: Int): Double =
    toBigDecimal()
        .divide(divisor.toBigDecimal(), 2, RoundingMode.HALF_UP)
        .toDouble()
