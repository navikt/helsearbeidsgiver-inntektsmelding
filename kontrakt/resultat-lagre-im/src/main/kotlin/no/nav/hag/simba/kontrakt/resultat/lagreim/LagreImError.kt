package no.nav.hag.simba.kontrakt.resultat.lagreim

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

@Serializable
data class LagreImError(
    @EncodeDefault
    val feiletValidering: String? = null,
)
