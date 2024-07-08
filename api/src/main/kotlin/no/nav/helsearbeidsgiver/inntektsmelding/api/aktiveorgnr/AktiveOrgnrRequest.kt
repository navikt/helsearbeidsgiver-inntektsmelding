package no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr

@Serializable
data class AktiveOrgnrRequest(
    val identitetsnummer: Fnr,
)
