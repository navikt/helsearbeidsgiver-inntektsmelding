package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class InnsendingProducer(
    private val rapid: RapidsConnection,
) {
    init {
        logger.info("Starter ${InnsendingProducer::class.simpleName}...")
    }

    fun publish(
        transaksjonId: UUID,
        skjemaInntektsmelding: SkjemaInntektsmelding,
        arbeidsgiverFnr: Fnr,
    ) {
        rapid
            .publish(
                Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
                Key.KONTEKST_ID to transaksjonId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.ARBEIDSGIVER_FNR to arbeidsgiverFnr.toJson(),
                        Key.SKJEMA_INNTEKTSMELDING to skjemaInntektsmelding.toJson(SkjemaInntektsmelding.serializer()),
                    ).toJson(),
            ).also {
                logger.info("Publiserte til kafka forespørselId: ${skjemaInntektsmelding.forespoerselId} og transaksjonId=$transaksjonId")
                sikkerLogger.info("Publiserte til kafka forespørselId: ${skjemaInntektsmelding.forespoerselId} json=${it.toPretty()}")
            }
    }
}
