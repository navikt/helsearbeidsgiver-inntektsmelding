package no.nav.helsearbeidsgiver.felles.domene

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

@Serializable
data class AktiveArbeidsgivere(
    val sykmeldtNavn: String?,
    val avsenderNavn: String?,
    val arbeidsgivere: List<Arbeidsgiver>,
) {
    @Serializable
    data class Arbeidsgiver(
        val orgnr: Orgnr,
        val orgNavn: String,
    )
}
