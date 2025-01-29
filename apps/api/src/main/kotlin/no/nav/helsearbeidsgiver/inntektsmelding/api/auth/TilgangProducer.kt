package no.nav.helsearbeidsgiver.inntektsmelding.api.auth

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.json.JsonElement
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
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

// TODO test
class TilgangProducer(
    private val rapid: RapidsConnection,
) {
    init {
        logger.info("Starter ${TilgangProducer::class.simpleName}...")
    }

    fun publishForespoerselId(
        kontekstId: UUID,
        fnr: Fnr,
        forespoerselId: UUID,
    ) = publish(
        EventName.TILGANG_FORESPOERSEL_REQUESTED,
        kontekstId,
        fnr,
        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
    )

    fun publishOrgnr(
        kontekstId: UUID,
        fnr: Fnr,
        orgnr: String,
    ) = publish(
        EventName.TILGANG_ORG_REQUESTED,
        kontekstId,
        fnr,
        Key.ORGNR_UNDERENHET to orgnr.toJson(),
    )

    private fun publish(
        eventName: EventName,
        kontekstId: UUID,
        fnr: Fnr,
        dataField: Pair<Key, JsonElement>,
    ) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
        ) {
            rapid
                .publish(
                    key = fnr,
                    Key.EVENT_NAME to eventName.toJson(),
                    Key.KONTEKST_ID to kontekstId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FNR to fnr.toJson(),
                            dataField,
                        ).toJson(),
                ).also { json ->
                    "Publiserte request om tilgang.".let {
                        logger.info(it)
                        sikkerLogger.info("$it\n${json.toPretty()}")
                    }
                }
        }
    }
}
