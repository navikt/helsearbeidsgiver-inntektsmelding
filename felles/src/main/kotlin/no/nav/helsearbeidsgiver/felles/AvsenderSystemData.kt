package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
data class AvsenderSystemData(
    val avsenderSystemNavn: String,
    val avsenderSystemVersjon: String,
    val arkivreferanse: String
)
