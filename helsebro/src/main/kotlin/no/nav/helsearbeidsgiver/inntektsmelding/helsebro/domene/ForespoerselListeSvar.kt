@file:UseSerializers(UuidSerializer::class, LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer

@Serializable
data class ForespoerselListeSvar(
    val resultat: List<Forespoersel>,
    val boomerang: JsonElement,
    val feil: Feil? = null,
) {
    enum class Feil {
        FORESPOERSEL_FOR_VEDTAKSPERIODE_ID_LISTE_FEILET,
    }
}
