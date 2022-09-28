@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.felles

data class Løsning(
    val value: Any? = null,
    val error: Feilmelding? = null
)

data class Feilmelding(
    val melding: String,
    val feltnavn: String? = null
)

data class Resultat(
    val løsninger: MutableMap<String, Løsning>
)
