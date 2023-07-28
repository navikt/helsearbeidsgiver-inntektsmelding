package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.util.UUID

class InntektProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter InntektProducer...")
    }

    fun publish(request: InntektRequest): UUID {
        val clientId = randomUuid()

        MdcUtils.withLogFields(
            "event_name" to EventName.INNTEKT_REQUESTED.name,
            "client_id" to clientId.toString(),
            "forespoersel_id" to request.forespoerselId.toString()
        ) {
            rapid.publish(
                Key.EVENT_NAME to EventName.INNTEKT_REQUESTED.toJson(),
                Key.CLIENT_ID to clientId.toJson(),
                DataFelt.FORESPOERSEL_ID to request.forespoerselId.toJson(),
                DataFelt.SKJAERINGSTIDSPUNKT to request.skjaeringstidspunkt.toJson()
            )
                .also { json ->
                    "Publiserte request om inntekt.".let {
                        logger.info(it)
                        sikkerLogger.info("it\n${json.toPretty()}")
                    }
                }
        }

        return clientId
    }
}
