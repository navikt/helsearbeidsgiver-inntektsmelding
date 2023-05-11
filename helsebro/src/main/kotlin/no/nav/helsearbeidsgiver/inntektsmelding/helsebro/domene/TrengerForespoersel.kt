@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import java.util.UUID

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class TrengerForespoersel(
    val forespoerselId: UUID,
    val boomerang: JsonElement
) {
    @SerialName("@behov")
    @EncodeDefault
    val behov = Pri.BehovType.TRENGER_FORESPØRSEL
}
