package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
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
        forespoerselId: UUID,
        request: Innsending,
        arbeidsgiverFnr: Fnr,
    ) {
        rapid
            .publish(
                Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                        Key.ARBEIDSGIVER_FNR to arbeidsgiverFnr.toJson(),
                        Key.SKJEMA_INNTEKTSMELDING to request.toJson(Innsending.serializer()),
                    ).toJson(),
            ).also {
                logger.info("Publiserte til kafka forespørselId: $forespoerselId og transaksjonId=$transaksjonId")
                sikkerLogger.info("Publiserte til kafka forespørselId: $forespoerselId json=${it.toPretty()}")
            }
    }
}
