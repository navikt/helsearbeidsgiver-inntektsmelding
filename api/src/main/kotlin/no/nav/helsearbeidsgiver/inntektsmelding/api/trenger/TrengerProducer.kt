package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import java.util.UUID

class TrengerProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter TrengerProducer...")
    }

    fun publish(request: TrengerRequest): UUID {
        val initiateId = UUID.randomUUID()

        rapid.publish(
            Key.BEHOV to listOf(BehovType.HENT_TRENGER_IM).toJson(BehovType::toJson),
            Key.FORESPOERSEL_ID to request.uuid.toJson(),
            Key.BOOMERANG to mapOf(
                Key.NESTE_BEHOV.str to listOf(BehovType.PREUTFYLL).toJson(BehovType::toJson),
                Key.INITIATE_ID.str to initiateId.toJson()
            ).toJson()
        ) {
            logger.info("Publiserte trenger behov id=$initiateId")
            sikkerlogg.info("Publiserte trenger behov id=$initiateId json=${it.toJson()}")
        }

        return initiateId
    }
}
