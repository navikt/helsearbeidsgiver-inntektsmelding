package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import java.time.LocalDateTime
import java.util.UUID

class InnsendingProducer(
    private val rapidsConnection: RapidsConnection
) {
    init {
        logger.info("Starter ${InnsendingProducer::class.simpleName}...")
    }

    fun publish(forespoerselId: UUID, request: InnsendingRequest, arbeidsgiverFnr: String): UUID {
        val clientId = UUID.randomUUID()
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.INSENDING_STARTED.name,
                Key.OPPRETTET.str to LocalDateTime.now(),
                Key.CLIENT_ID.str to clientId,
                Key.FORESPOERSEL_ID.str to forespoerselId,
                DataFelt.ORGNRUNDERENHET.str to request.orgnrUnderenhet,
                Key.IDENTITETSNUMMER.str to request.identitetsnummer,
                Key.ARBEIDSGIVER_ID.str to arbeidsgiverFnr,
                DataFelt.INNTEKTSMELDING.str to request
            )
        )
        rapidsConnection.publish(packet.toJson())
        logger.info("Publiserte til kafka forespørselId: $forespoerselId og clientId=$clientId")
        sikkerLogger.info("Publiserte til kafka forespørselId: $forespoerselId json=${packet.toPretty()}")
        return clientId
    }
}
