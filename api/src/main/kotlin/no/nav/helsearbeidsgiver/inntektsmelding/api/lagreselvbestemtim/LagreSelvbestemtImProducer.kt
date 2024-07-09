package no.nav.helsearbeidsgiver.inntektsmelding.api.lagreselvbestemtim

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
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

class LagreSelvbestemtImProducer(
    private val rapid: RapidsConnection,
) {
    init {
        logger.info("Starter ${LagreSelvbestemtImProducer::class.simpleName}...")
    }

    fun publish(
        clientId: UUID,
        skjema: SkjemaInntektsmeldingSelvbestemt,
        avsenderFnr: Fnr,
    ) {
        MdcUtils.withLogFields(
            Log.event(EventName.SELVBESTEMT_IM_MOTTATT),
            Log.clientId(clientId),
        ) {
            rapid.publish(
                Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
                Key.CLIENT_ID to clientId.toJson(),
                Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmeldingSelvbestemt.serializer()),
                Key.ARBEIDSGIVER_FNR to avsenderFnr.toJson(),
            )
                .also {
                    logger.info("Publiserte til kafka.")
                    sikkerLogger.info("Publiserte til kafka:\n${it.toPretty()}")
                }
        }
    }
}
