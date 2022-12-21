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
    val orgnr: String,
    val fnr: String,
    val vedtaksperiodeId: UUID,
    val boomerang: Map<String, JsonElement>
) {
    val behov = Pri.BehovType.TRENGER_FORESPØRSEL

    fun toJson(): JsonElement =
        jsonOf(
            Pri.Key.BEHOV to behov.toJson(),
            Pri.Key.ORGNR to orgnr.toJson(),
            Pri.Key.FNR to fnr.toJson(),
            Pri.Key.VEDTAKSPERIODE_ID to vedtaksperiodeId.toJson(),
            Pri.Key.BOOMERANG to boomerang.toJson()
        )
}
