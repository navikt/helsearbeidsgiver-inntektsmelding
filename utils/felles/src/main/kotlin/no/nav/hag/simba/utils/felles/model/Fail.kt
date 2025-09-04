@file:UseSerializers(UuidSerializer::class)

package no.nav.hag.simba.utils.felles.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

@Serializable
data class Fail(
    val feilmelding: String,
    val kontekstId: UUID,
    val utloesendeMelding: Map<Key, JsonElement>,
) {
    fun tilMelding(): Map<Key, JsonElement> =
        mapOf(
            Key.FAIL to toJson(serializer()),
        )

    fun utloesendeMeldingMedData(): Map<Key, JsonElement> = utloesendeMelding[Key.DATA]?.toMap().orEmpty().plus(utloesendeMelding)
}
