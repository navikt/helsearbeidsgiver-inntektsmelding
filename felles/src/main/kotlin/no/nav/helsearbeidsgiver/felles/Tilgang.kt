package no.nav.helsearbeidsgiver.felles

import com.fasterxml.jackson.annotation.JsonValue

enum class Tilgang(
    @JsonValue
    val value: String
) {
    HAR_TILGANG("HAR_TILGANG"),
    IKKE_TILGANG("IKKE_TILGANG")
}
