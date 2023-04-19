package no.nav.helsearbeidsgiver.felles.utils

/** Forhindrer avrundingsfeil. */
fun List<Double>.sumMoney(): Double =
    sumOf(Double::toBigDecimal)
        .toDouble()
