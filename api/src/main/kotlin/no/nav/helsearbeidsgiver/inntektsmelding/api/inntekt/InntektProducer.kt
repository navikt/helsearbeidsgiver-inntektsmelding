package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

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

class InntektProducer(
    private val rapid: RapidsConnection,
) {
    init {
        logger.info("Starter ${InntektProducer::class.simpleName}...")
    }

    fun publish(
        transaksjonId: UUID,
        request: InntektRequest,
    ) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.INNTEKT_REQUESTED),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(request.forespoerselId),
        ) {
            rapid
                .publish(
                    Key.EVENT_NAME to EventName.INNTEKT_REQUESTED.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_ID to request.forespoerselId.toJson(),
                            Key.INNTEKTSDATO to request.skjaeringstidspunkt.toJson(),
                        ).toJson(),
                ).also { json ->
                    "Publiserte request om inntekt.".let {
                        logger.info(it)
                        sikkerLogger.info("$it\n${json.toPretty()}")
                    }
                }
        }
    }
}
