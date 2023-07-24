@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

sealed class Løsning {
    abstract val value: Any?
    abstract val error: Feilmelding?
}

data class Data<T>(
    val t: T? = null
)

@Serializable
data class Feilmelding(
    val melding: String,
    val status: Int? = null,
    val datafelt: DataFelt? = null
)

@Serializable
data class FeilReport(
    val feil: MutableList<Feilmelding> = mutableListOf()
) {
    fun status(): Int {
        return feil.find { it.status ?: 0 < 0 }?.status ?: 0
    }
}

@Serializable
data class NavnLøsning(
    override val value: PersonDato? = null,
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
data class HentTrengerImLøsning(
    override val value: TrengerInntekt? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class PreutfyltLøsning(
    override val value: PersonLink? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class HentImOrgnrLøsning(
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class TilgangskontrollLøsning(
    override val value: Tilgang? = null,
    override val error: Feilmelding? = null
) : Løsning()
