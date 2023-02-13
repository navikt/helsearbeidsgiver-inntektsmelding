package no.nav.helsearbeidsgiver.felles.inntektsmelding.felles

import kotlinx.serialization.Serializable

@Serializable
enum class ÅrsakBeregnetInntektEndringKodeliste {
    Tariffendring,
    FeilInntekt
}
