@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene

import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.jsonOf
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
import java.util.UUID

data class TrengerForespoersel(
    val orgnr: String,
    val fnr: String,
    val vedtaksperiodeId: UUID
) {
    val behov = Pri.BehovType.TRENGER_FORESPØRSEL

    fun toJson(): String =
        jsonOf(
            Pri.Key.BEHOV to behov.name,
            Pri.Key.ORGNR to orgnr,
            Pri.Key.FNR to fnr,
            Pri.Key.VEDTAKSPERIODE_ID to vedtaksperiodeId.toString()
        )
}
