package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

private val orgnrRgx = Regex("\\d{9}")
private val fnrRgx = Regex(
    "(?:0[1-9]|[12]\\d|3[01])" + // to første siffer er gyldig dag
        "(?:0[1-9]|1[012])" + // to neste siffer er gyldig måned
        "\\d{7}" // resten er tall (siste del kan forbedres)
)

@Serializable
@JvmInline
value class Orgnr(val verdi: String) {
    init {
        require(verdi.matches(orgnrRgx))
    }

    override fun toString(): String =
        verdi
}

@Serializable
@JvmInline
value class Fnr(val verdi: String) {
    init {
        require(verdi.matches(fnrRgx))
    }

    override fun toString(): String =
        verdi
}
