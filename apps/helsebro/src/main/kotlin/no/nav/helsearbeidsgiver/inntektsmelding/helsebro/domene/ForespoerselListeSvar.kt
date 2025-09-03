@file:UseSerializers(UuidSerializer::class, LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.domene.ForespoerselFraBro
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer

@Serializable
data class ForespoerselListeSvar(
    val resultat: List<ForespoerselFraBro>,
    val feil: Feil? = null,
    val boomerang: JsonElement,
) {
    enum class Feil {
        FORESPOERSEL_FOR_VEDTAKSPERIODE_ID_LISTE_FEILET,
    }
}
