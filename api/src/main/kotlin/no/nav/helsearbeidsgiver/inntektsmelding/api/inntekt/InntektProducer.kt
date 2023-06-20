package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class InntektProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter InntektProducer...")
    }

    fun publish(request: InntektRequest): UUID {
        val initiateId = UUID.randomUUID()
        val spleisForesporselId = request.forespoerselId

        rapid.publish(
            Key.EVENT_NAME to EventName.INNTEKT_REQUESTED.toJson(EventName.serializer()),
            Key.BEHOV to listOf(BehovType.HENT_TRENGER_IM).toJson(BehovType.serializer()),
            Key.FORESPOERSEL_ID to spleisForesporselId.toJson(),
            Key.BOOMERANG to mapOf(
                Key.NESTE_BEHOV.str to listOf(BehovType.INNTEKT).toJson(BehovType.serializer()),
                Key.INITIATE_ID.str to initiateId.toJson(UuidSerializer), // Akkumulator velger denne som ny UUID v / neste behov!
                Key.INITIATE_EVENT.str to EventName.INNTEKT_REQUESTED.toJson(EventName.serializer()),
                Key.INNTEKT_DATO.str to request.skjaeringstidspunkt.toJson(LocalDateSerializer)
            ).toJson()
        )

        logger.info("Publiserte Behov: ${BehovType.HENT_TRENGER_IM} for spleisId $spleisForesporselId (oppdater inntekt)")

        return initiateId
    }
}
