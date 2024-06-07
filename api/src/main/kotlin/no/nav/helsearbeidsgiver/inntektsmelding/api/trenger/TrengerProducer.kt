package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import java.util.UUID

class TrengerProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter ${TrengerProducer::class.simpleName}...")
    }

    fun publish(request: HentForespoerselRequest, arbeidsgiverFnr: String): UUID {
        val transaksjonId = UUID.randomUUID()

        rapid.publish(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(EventName.serializer()),
            Key.UUID to transaksjonId.toString().toJson(),
            Key.FORESPOERSEL_ID to request.uuid.toJson(),
            Key.ARBEIDSGIVER_ID to arbeidsgiverFnr.toJson()
        )
            .also {
                logger.info("Publiserte trenger behov med transaksjonId=$transaksjonId")
                sikkerLogger.info("Publiserte trenger behov med transaksjonId=$transaksjonId json=${it.toPretty()}")
            }

        return transaksjonId
    }
}
