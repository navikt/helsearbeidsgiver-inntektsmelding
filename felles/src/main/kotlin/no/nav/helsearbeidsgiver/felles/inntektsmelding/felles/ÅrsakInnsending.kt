package no.nav.helsearbeidsgiver.felles.inntektsmelding.felles

import kotlinx.serialization.Serializable

@Serializable
enum class ÅrsakInnsending {
    Ny,
    Endring
}
