package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import java.util.UUID

class InntektProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter InntektProducer...")
    }

    fun publish(request: InntektRequest): UUID {
        val loesningId = UUID.randomUUID()
        val spleisForesporselId = request.forespoerselId
        rapid.publish(
            Key.BEHOV to listOf(BehovType.HENT_TRENGER_IM).toJson(BehovType.serializer()),
            Key.FORESPOERSEL_ID to spleisForesporselId.toJson(),
            Key.BOOMERANG to mapOf(
                Key.INITIATE_ID.str to spleisForesporselId.toJson(UuidSerializer), // Akkumulator velger denne som ny UUID v / neste behov!
                Key.NESTE_BEHOV.str to listOf(BehovType.INNTEKT).toJson(BehovType.serializer()),
                Key.INNTEKT_DATO.str to request.skjaeringstidspunkt.toJson(LocalDateSerializer)
            ).toJson()
        ) {
            logger.info("Publiserte Behov: ${BehovType.HENT_TRENGER_IM} for spleisId $spleisForesporselId (oppdater inntekt)")
        }
        return loesningId
    }
}
