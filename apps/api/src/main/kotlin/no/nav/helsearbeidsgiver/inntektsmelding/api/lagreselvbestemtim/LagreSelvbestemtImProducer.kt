package no.nav.helsearbeidsgiver.inntektsmelding.api.lagreselvbestemtim

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
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
import java.time.LocalDateTime
import java.util.UUID

class LagreSelvbestemtImProducer(
    private val rapid: RapidsConnection,
) {
    init {
        logger.info("Starter ${LagreSelvbestemtImProducer::class.simpleName}...")
    }

    fun publish(
        transaksjonId: UUID,
        avsenderFnr: Fnr,
        skjema: SkjemaInntektsmeldingSelvbestemt,
        mottatt: LocalDateTime,
    ) {
        MdcUtils.withLogFields(
            Log.event(EventName.SELVBESTEMT_IM_MOTTATT),
            Log.transaksjonId(transaksjonId),
        ) {
            rapid
                .publish(
                    key = skjema.sykmeldtFnr,
                    Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
                    Key.KONTEKST_ID to transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.ARBEIDSGIVER_FNR to avsenderFnr.toJson(),
                            Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmeldingSelvbestemt.serializer()),
                            Key.MOTTATT to mottatt.toJson(),
                        ).toJson(),
                ).also {
                    logger.info("Publiserte til kafka.")
                    sikkerLogger.info("Publiserte til kafka:\n${it.toPretty()}")
                }
        }
    }
}
