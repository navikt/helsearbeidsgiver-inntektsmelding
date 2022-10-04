@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.felles

data class Løsning(
    val behovType: BehovType,
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
