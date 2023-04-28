package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import java.time.LocalDate

fun MockPerioder(): List<Periode> {
    val dato = LocalDate.now()
    return listOf(
        Periode(dato, dato.plusDays(20)),
        Periode(dato.plusDays(10), dato.plusDays(20)),
        Periode(dato.plusDays(20), dato.plusDays(30))
    )
}
