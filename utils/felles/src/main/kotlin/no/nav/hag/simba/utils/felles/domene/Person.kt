package no.nav.hag.simba.utils.felles.domene

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr

@Serializable
data class Person(
    val fnr: Fnr,
    val navn: String,
)
