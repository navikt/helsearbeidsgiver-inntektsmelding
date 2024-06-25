package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoersel

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import java.util.UUID

class HentForespoerselProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter ${HentForespoerselProducer::class.simpleName}...")
    }

    fun publish(transaksjonId: UUID, request: HentForespoerselRequest, arbeidsgiverFnr: String) {
        rapid.publish(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(EventName.serializer()),
            Key.UUID to transaksjonId.toString().toJson(),
            Key.DATA to "".toJson(),
            Key.FORESPOERSEL_ID to request.uuid.toJson(),
            Key.ARBEIDSGIVER_ID to arbeidsgiverFnr.toJson()
        )
            .also {
                logger.info("Publiserte trenger behov med transaksjonId=$transaksjonId")
                sikkerLogger.info("Publiserte trenger behov med transaksjonId=$transaksjonId json=${it.toPretty()}")
            }
    }
}
