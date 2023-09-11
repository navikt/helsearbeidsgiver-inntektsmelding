package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

private val orgnrRgx = Regex("\\d{9}")

private val fnrRgx = Regex(
    "(?:[04][1-9]|[1256]\\d|[37][01])" + // to første siffer er gyldig dag
        "(?:[048][1-9]|[159][012])" + // to neste siffer er gyldig måned, med støtte for testpersoner (+40 for NAV, +80 for TestNorge)
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
