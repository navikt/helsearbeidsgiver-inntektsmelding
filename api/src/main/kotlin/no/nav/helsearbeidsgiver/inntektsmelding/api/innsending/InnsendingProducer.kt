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
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter ${InnsendingProducer::class.simpleName}...")
    }

    fun publish(clientId: UUID, forespoerselId: UUID, request: Innsending, arbeidsgiverFnr: Fnr) {
        rapid.publish(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.CLIENT_ID to clientId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.ORGNRUNDERENHET to request.orgnrUnderenhet.toJson(),
            Key.IDENTITETSNUMMER to request.identitetsnummer.toJson(),
            Key.ARBEIDSGIVER_ID to arbeidsgiverFnr.toJson(),
            Key.SKJEMA_INNTEKTSMELDING to request.toJson(Innsending.serializer())
        )
            .also {
                logger.info("Publiserte til kafka forespørselId: $forespoerselId og clientId=$clientId")
                sikkerLogger.info("Publiserte til kafka forespørselId: $forespoerselId json=${it.toPretty()}")
            }
    }
}
