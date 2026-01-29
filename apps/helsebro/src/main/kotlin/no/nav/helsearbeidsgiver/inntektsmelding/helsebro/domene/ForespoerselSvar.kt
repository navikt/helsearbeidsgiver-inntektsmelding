@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.bro.forespoersel.ForespoerselFraBro
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.util.UUID

@Serializable
data class ForespoerselSvar(
    val forespoerselId: UUID,
    val resultat: ForespoerselFraBro? = null,
    val feil: Feil? = null,
    val boomerang: JsonElement,
) {
    enum class Feil {
        FORESPOERSEL_IKKE_FUNNET,
    }
}
