package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import kotlinx.serialization.Serializable

@Serializable
enum class ÅrsakInnsending {
    Ny,
    Endring
}
