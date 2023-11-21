package no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr

import kotlinx.serialization.Serializable

@Serializable
data class AktiveOrgnrRequest (
    val fnr: String
)

