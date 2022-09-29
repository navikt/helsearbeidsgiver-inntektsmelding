@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.felles

data class Løsning(
    val behov: String,
    val value: Any? = null,
    val error: Feilmelding? = null
)

data class Feilmelding(
    val melding: String,
    val status: Int? = null
)

data class Resultat(
    val løsninger: List<Løsning>
)
