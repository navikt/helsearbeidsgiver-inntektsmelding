@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

sealed class Løsning {
    abstract val value: Any?
    abstract val error: Feilmelding?
}

@Serializable
data class Feilmelding(
    val melding: String,
    val status: Int? = null
)

@Serializable
data class NavnLøsning(
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class VirksomhetLøsning(
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class InntektLøsning(
    override val value: Inntekt? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class ArbeidsforholdLøsning(
    override val value: List<Arbeidsforhold> = emptyList(),
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class JournalpostLøsning(
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class NotifikasjonLøsning(
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class HentTrengerImLøsning(
    override val value: TrengerInntekt? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class PreutfyltLøsning(
    override val value: PersonLink? = null,
    override val error: Feilmelding? = null
) : Løsning()
