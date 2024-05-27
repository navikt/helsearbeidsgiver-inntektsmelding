package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
data class Feilmelding(
    val melding: String,
    // TODO fjern når frontend ikke lenger bruker
    val status: Int? = null,
    val datafelt: Key? = null
)

@Serializable
data class FeilReport(
    val feil: MutableList<Feilmelding> = mutableListOf()
)
