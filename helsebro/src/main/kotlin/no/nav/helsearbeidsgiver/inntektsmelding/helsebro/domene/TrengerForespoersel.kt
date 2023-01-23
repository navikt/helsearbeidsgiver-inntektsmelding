@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene

import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.jsonOf
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
import java.util.UUID

data class TrengerForespoersel(
    val forespoerselId: UUID,
    val boomerang: Map<String, JsonElement>
) {
    val behov = Pri.BehovType.TRENGER_FORESPØRSEL

    fun toJson(): JsonElement =
        jsonOf(
            Pri.Key.BEHOV to behov.toJson(),
            Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Pri.Key.BOOMERANG to boomerang.toJson()
        )
}
