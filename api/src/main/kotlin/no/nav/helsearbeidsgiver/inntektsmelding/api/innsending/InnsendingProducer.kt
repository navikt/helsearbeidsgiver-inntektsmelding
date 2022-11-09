package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import java.time.LocalDateTime
import java.util.UUID

class InnsendingProducer(
    private val rapidsConnection: RapidsConnection
) {
    init {
        logger.info("Starter InnsendingProducer...")
    }

    fun publish(request: InnsendingRequest): String {
        val uuid = UUID.randomUUID()
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                "@event_name" to "inntektsmelding_inn",
                "@behov" to listOf(
                    BehovType.VIRKSOMHET.name
                ),
                // "@extra" to BehovType.JOURNALFOER.name,
                "@id" to uuid,
                "@opprettet" to LocalDateTime.now(),
                "uuid" to uuid,
                "orgnrUnderenhet" to request.orgnrUnderenhet,
                "inntektsmelding" to request
            )
        )
        rapidsConnection.publish(request.identitetsnummer, packet.toJson())
        logger.info("Publiserte til kafka id=$uuid")
        sikkerlogg.info("Publiserte til kafka id=$uuid json=${packet.toJson()}")
        return uuid.toString()
    }
}
