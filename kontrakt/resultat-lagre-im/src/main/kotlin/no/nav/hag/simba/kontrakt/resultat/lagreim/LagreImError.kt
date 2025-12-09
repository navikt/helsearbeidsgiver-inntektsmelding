package no.nav.hag.simba.kontrakt.resultat.lagreim

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class LagreImError(
    @EncodeDefault
    val feiletValidering: String? = null,
)
