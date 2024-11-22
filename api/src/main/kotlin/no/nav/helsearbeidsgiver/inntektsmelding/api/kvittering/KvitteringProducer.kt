package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
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

class KvitteringProducer(
    private val rapid: RapidsConnection,
) {
    init {
        logger.info("Starter ${KvitteringProducer::class.simpleName}...")
    }

    fun publish(
        transaksjonId: UUID,
        forespoerselId: UUID,
    ) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.KVITTERING_REQUESTED),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        ) {
            rapid
                .publish(
                    key = forespoerselId,
                    Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
                    Key.KONTEKST_ID to transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                        ).toJson(),
                ).also { json ->
                    "Publiserte request om kvittering.".let {
                        logger.info(it)
                        sikkerLogger.info("$it\n${json.toPretty()}")
                    }
                }
        }
    }
}
