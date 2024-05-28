package no.nav.helsearbeidsgiver.inntektsmelding.api.hentselvbestemtim

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

class HentSelvbestemtImProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter ${HentSelvbestemtImProducer::class.simpleName}...")
    }

    fun publish(selvbestemtId: UUID): UUID {
        val clientId = UUID.randomUUID()

        MdcUtils.withLogFields(
            Log.event(EventName.SELVBESTEMT_IM_REQUESTED),
            Log.clientId(clientId),
            Log.selvbestemtId(selvbestemtId)
        ) {
            rapid.publish(
                Key.EVENT_NAME to EventName.SELVBESTEMT_IM_REQUESTED.toJson(),
                Key.CLIENT_ID to clientId.toJson(),
                Key.SELVBESTEMT_ID to selvbestemtId.toJson()
            )
                .also {
                    logger.info("Publiserte til kafka.")
                    sikkerLogger.info("Publiserte til kafka:\n${it.toPretty()}")
                }
        }

        return clientId
    }
}
