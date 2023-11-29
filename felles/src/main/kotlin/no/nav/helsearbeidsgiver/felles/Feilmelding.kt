package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

data class Data<T>(
    val t: T? = null
)

@Serializable
data class Feilmelding(
    val melding: String,
    val status: Int? = null,
    val datafelt: Key? = null
)

@Serializable
data class FeilReport(
    val feil: MutableList<Feilmelding> = mutableListOf()
) {
    fun status(): Int =
        feil.mapNotNull { it.status }.find { it < 0 } ?: 0
}
