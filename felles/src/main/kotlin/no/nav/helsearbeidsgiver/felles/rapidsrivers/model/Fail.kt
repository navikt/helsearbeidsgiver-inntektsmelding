@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

private val sikkerLogger = sikkerLogger()

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
                Key.FAIL to fail.toJson(serializer()),
                Key.UUID to fail.transaksjonId.let {
                    if (it != null) {
                        it
                    } else {
                        sikkerLogger.error("Mangler transaksjonId i Fail som ble forårsaket av\n${fail.utloesendeMelding.toPretty()}")
                        randomUuid()
                    }
                }
                    .toJson(),
                Key.FORESPOERSEL_ID to fail.forespoerselId.let {
                    if (it != null) {
                        it
                    } else {
                        sikkerLogger.error("Mangler forespoerselId i Fail som ble forårsaket av\n${fail.utloesendeMelding.toPretty()}")
                        randomUuid()
                    }
                }
                    .toJson()
            )
    }
}
