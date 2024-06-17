package no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
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
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class TilgangProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter ${TilgangProducer::class.simpleName}...")
    }

    fun publishForespoerselId(forespoerselId: UUID, fnr: Fnr): UUID =
        publish(
            EventName.TILGANG_FORESPOERSEL_REQUESTED,
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.FNR to fnr.toJson()
        )

    fun publishOrgnr(orgnr: String, fnr: Fnr): UUID =
        publish(
            EventName.TILGANG_ORG_REQUESTED,
            Key.ORGNRUNDERENHET to orgnr.toJson(),
            Key.FNR to fnr.toJson()
        )

    private fun publish(eventName: EventName, vararg messageFields: Pair<Key, JsonElement>): UUID {
        val clientId = randomUuid()

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

        return clientId
    }
}
