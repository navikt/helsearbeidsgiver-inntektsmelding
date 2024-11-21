package no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang

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

class TilgangProducer(
    private val rapid: RapidsConnection,
) {
    init {
        logger.info("Starter ${TilgangProducer::class.simpleName}...")
    }

    fun publishForespoerselId(
        transaksjonId: UUID,
        fnr: Fnr,
        forespoerselId: UUID,
    ) = publish(
        EventName.TILGANG_FORESPOERSEL_REQUESTED,
        transaksjonId,
        Key.FNR to fnr.toJson(),
        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
    )

    fun publishOrgnr(
        transaksjonId: UUID,
        fnr: Fnr,
        orgnr: String,
    ) = publish(
        EventName.TILGANG_ORG_REQUESTED,
        transaksjonId,
        Key.FNR to fnr.toJson(),
        Key.ORGNRUNDERENHET to orgnr.toJson(),
    )

    private fun publish(
        eventName: EventName,
        transaksjonId: UUID,
        vararg messageFields: Pair<Key, JsonElement>,
    ) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
        ) {
            rapid
                .publish(
                    Key.EVENT_NAME to eventName.toJson(),
                    Key.KONTEKST_ID to transaksjonId.toJson(),
                    Key.DATA to messageFields.toMap().toJson(),
                ).also { json ->
                    "Publiserte request om tilgang.".let {
                        logger.info(it)
                        sikkerLogger.info("$it\n${json.toPretty()}")
                    }
                }
        }
    }
}
