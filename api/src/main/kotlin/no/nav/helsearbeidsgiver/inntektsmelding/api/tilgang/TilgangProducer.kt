package no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang

import kotlinx.serialization.json.JsonElement
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

class TilgangProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter ${TilgangProducer::class.simpleName}...")
    }

    fun publishForespoerselId(clientId: UUID, fnr: String, forespoerselId: UUID) =
        publish(
            EventName.TILGANG_FORESPOERSEL_REQUESTED,
            clientId,
            Key.FNR to fnr.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson()
        )

    fun publishOrgnr(clientId: UUID, fnr: String, orgnr: String) =
        publish(
            EventName.TILGANG_ORG_REQUESTED,
            clientId,
            Key.FNR to fnr.toJson(),
            Key.ORGNRUNDERENHET to orgnr.toJson()
        )

    private fun publish(eventName: EventName, clientId: UUID, vararg messageFields: Pair<Key, JsonElement>) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.clientId(clientId)
        ) {
            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.CLIENT_ID to clientId.toJson(),
                *messageFields
            )
                .also { json ->
                    "Publiserte request om tilgang.".let {
                        logger.info(it)
                        sikkerLogger.info("$it\n${json.toPretty()}")
                    }
                }
        }
    }
}
