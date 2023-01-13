package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable

@Serializable
data class PersonLink(
    val fnr: String,
    val orgnr: String
)
