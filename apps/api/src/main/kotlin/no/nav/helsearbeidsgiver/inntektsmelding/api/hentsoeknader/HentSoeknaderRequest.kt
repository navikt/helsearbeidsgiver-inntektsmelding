package no.nav.helsearbeidsgiver.inntektsmelding.api.hentsoeknader

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

@Serializable
data class HentSoeknaderRequest(
    val orgnr: Orgnr,
    val sykmeldtFnr: Fnr,
    val erBehandlingsdager: Boolean,
)
