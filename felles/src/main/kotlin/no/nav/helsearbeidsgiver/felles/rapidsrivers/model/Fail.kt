@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

@Serializable
data class Fail(
    val feilmelding: String,
    val event: EventName,
    val transaksjonId: UUID,
    val forespoerselId: UUID?,
    val utloesendeMelding: JsonElement
) {
    fun tilMelding(): Map<Key, JsonElement> =
        mapOf(
            Key.FAIL to toJson(serializer()),
            Key.EVENT_NAME to event.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId?.toJson()
        )
            .mapValuesNotNull { it }
}
