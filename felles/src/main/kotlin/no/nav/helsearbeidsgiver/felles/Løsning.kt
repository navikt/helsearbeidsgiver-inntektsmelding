package no.nav.helsearbeidsgiver.felles

data class Løsning(
    val value: Any? = null,
    val errors: List<Feilmelding> = emptyList()
)

data class Feilmelding(
    val melding: String,
    val feltnavn: String? = null
)
