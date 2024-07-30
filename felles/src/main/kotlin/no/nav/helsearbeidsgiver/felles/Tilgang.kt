package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
enum class Tilgang {
    HAR_TILGANG,
    IKKE_TILGANG,
}
