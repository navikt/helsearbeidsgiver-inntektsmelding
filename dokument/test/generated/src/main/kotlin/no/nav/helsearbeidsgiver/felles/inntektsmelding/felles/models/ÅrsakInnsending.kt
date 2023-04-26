package no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models

import com.fasterxml.jackson.annotation.JsonValue
import kotlin.String
import kotlin.collections.Map

enum class ÅrsakInnsending(
    @JsonValue
    val value: String
) {
    NY("Ny"),

    ENDRING("Endring");

    companion object {
        private val mapping: Map<String, ÅrsakInnsending> = values().associateBy(ÅrsakInnsending::value)

        fun fromValue(value: String): ÅrsakInnsending? = mapping[value]
    }
}
