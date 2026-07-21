package no.nav.hag.simba.kontrakt.resultat.lagreim

import kotlinx.serialization.Serializable

@Serializable
data class LagreImError(
    val valideringsfeil: Set<String>,
)
