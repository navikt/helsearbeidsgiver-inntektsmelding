package no.nav.helsearbeidsgiver.inntektsmelding.api.hentaapenim

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.util.UUID

// TODO test
class HentAapenImProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter ${HentAapenImProducer::class.simpleName}...")
    }

    fun publish(aapenId: UUID): UUID {
        val clientId = UUID.randomUUID()

        MdcUtils.withLogFields(
            Log.event(EventName.AAPEN_IM_REQUESTED),
            Log.clientId(clientId),
            Log.aapenId(aapenId)
        ) {
            rapid.publish(
                Key.EVENT_NAME to EventName.AAPEN_IM_REQUESTED.toJson(),
                Key.CLIENT_ID to clientId.toJson(),
                Key.AAPEN_ID to aapenId.toJson()
            )
                .also {
                    logger.info("Publiserte til kafka.")
                    sikkerLogger.info("Publiserte til kafka:\n${it.toPretty()}")
                }
        }

        return clientId
    }
}
