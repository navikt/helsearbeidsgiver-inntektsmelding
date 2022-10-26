@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.felles

sealed class Løsning {
    abstract val value: Any?
    abstract val error: Feilmelding?
}

data class Feilmelding(
    val melding: String,
    val status: Int? = null
)

data class NavnLøsning(
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()

data class VirksomhetLøsning(
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()

data class InntektLøsning(
    override val value: Inntekt? = null,
    override val error: Feilmelding? = null
) : Løsning()

data class ArbeidsforholdLøsning(
    override val value: List<Arbeidsforhold> = emptyList(),
    override val error: Feilmelding? = null
) : Løsning()

data class SykLøsning(
    override val value: Syk? = null,
    override val error: Feilmelding? = null
) : Løsning()

data class EgenmeldingLøsning(
    override val value: List<MottattPeriode>? = null,
    override val error: Feilmelding? = null
) : Løsning()
