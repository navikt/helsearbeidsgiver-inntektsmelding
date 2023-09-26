package no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.util.UUID

class TilgangProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter TilgangProducer...")
    }

    fun publish(forespoerselId: UUID, fnr: String): UUID {
        val clientId = randomUuid()

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.TILGANG_REQUESTED),
            Log.clientId(clientId),
            Log.forespoerselId(forespoerselId)
        ) {
            rapid.publish(
                Key.EVENT_NAME to EventName.TILGANG_REQUESTED.toJson(),
                Key.CLIENT_ID to clientId.toJson(),
                DataFelt.FORESPOERSEL_ID to forespoerselId.toJson(),
                DataFelt.FNR to fnr.toJson()
            )
                .also { json ->
                    "Publiserte request om tilgang.".let {
                        logger.info(it)
                        sikkerLogger.info("$it\n${json.toPretty()}")
                    }
                }
        }

        return clientId
    }
}
