@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

@Serializable
data class Fail(
    val feilmelding: String,
    val event: EventName,
    val transaksjonId: UUID?,
    val forespoerselId: UUID?,
    val utloesendeMelding: JsonElement
) {
    companion object {
        fun MessageContext.publish(fail: Fail): JsonElement =
            publish(
                Key.FAIL to fail.toJson(serializer())
            )
    }
}
