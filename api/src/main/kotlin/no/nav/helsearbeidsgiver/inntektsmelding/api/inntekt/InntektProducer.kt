package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger

class InntektProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter InntektProducer...")
    }

    fun publish(request: InntektRequest): String {
        val uuid = request.uuid.toJson()
        rapid.publish(
            Key.BEHOV to listOf(BehovType.HENT_TRENGER_IM).toJson(BehovType.serializer()),
            Key.FORESPOERSEL_ID to uuid,
            Key.BOOMERANG to mapOf(
                Key.NESTE_BEHOV.str to listOf(BehovType.INNTEKT).toJson(BehovType.serializer()),
                Key.INNTEKT_DATO.str to request.fom.toJson(LocalDateSerializer)
            ).toJson()
        ) {
            logger.info("Publiserte Behov: " + BehovType.HENT_TRENGER_IM.name + " for uuid $uuid (oppdater inntekt)")
        }
        return uuid.toString()
    }
}
