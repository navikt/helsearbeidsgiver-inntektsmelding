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

    fun publish(request: TrengerRequest, initiateId: UUID = UUID.randomUUID()): UUID {
        rapid.publish(
            Key.EVENT_NAME to "TRENGER_REQUESTED".toJson(),
            Key.BEHOV to listOf(BehovType.HENT_TRENGER_IM).toJson(BehovType.serializer()),
            Key.FORESPOERSEL_ID to request.uuid.toJson(),
            Key.BOOMERANG to mapOf(
                Key.NESTE_BEHOV.str to listOf(BehovType.PREUTFYLL).toJson(BehovType.serializer()),
                Key.INITIATE_ID.str to initiateId.toJson()
            ).toJson()
        ) {
            logger.info("Publiserte trenger behov id=$initiateId")
            sikkerlogg.info("Publiserte trenger behov id=$initiateId json=${it.toJson()}")
        }

        return initiateId
    }
}
