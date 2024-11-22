package no.nav.helsearbeidsgiver.inntektsmelding.api.inntektselvbestemt

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

class InntektSelvbestemtProducer(
    private val rapid: RapidsConnection,
) {
    init {
        logger.info("Starter ${InntektSelvbestemtProducer::class.simpleName}...")
    }

    fun publish(
        transaksjonId: UUID,
        request: InntektSelvbestemtRequest,
    ) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.INNTEKT_SELVBESTEMT_REQUESTED),
            Log.transaksjonId(transaksjonId),
        ) {
            rapid
                .publish(
                    key = request.sykmeldtFnr,
                    Key.EVENT_NAME to EventName.INNTEKT_SELVBESTEMT_REQUESTED.toJson(),
                    Key.KONTEKST_ID to transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FNR to request.sykmeldtFnr.toJson(),
                            Key.ORGNRUNDERENHET to request.orgnr.toJson(),
                            Key.INNTEKTSDATO to request.inntektsdato.toJson(),
                        ).toJson(),
                ).also { json ->
                    "Publiserte request om inntekt selvbestemt.".let {
                        logger.info(it)
                        sikkerLogger.info("$it\n${json.toPretty()}")
                    }
                }
        }
    }
}
