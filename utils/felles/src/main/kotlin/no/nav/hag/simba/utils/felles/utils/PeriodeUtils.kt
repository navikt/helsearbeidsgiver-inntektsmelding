package no.nav.hag.simba.utils.felles.utils

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val norskDatoFormatKort = DateTimeFormatter.ofPattern("dd.MM.yy")

fun List<Periode>.tilKortFormat(): String {
    val fom = firstOrNull()?.fom?.tilNorskFormatKort()
    val tom = lastOrNull()?.tom?.tilNorskFormatKort()

    val ellipse =
        if (size > 1) {
            "[…]"
        } else {
            null
        }

    return listOfNotNull(fom, ellipse, tom).joinToString(separator = "–")
}

private fun LocalDate.tilNorskFormatKort(): String = format(norskDatoFormatKort)
