package no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models

import kotlinx.serialization.Serializable

@Serializable
enum class ÅrsakBeregnetInntektEndringKodeliste {
    Tariffendring,
    FeilInntekt
}
