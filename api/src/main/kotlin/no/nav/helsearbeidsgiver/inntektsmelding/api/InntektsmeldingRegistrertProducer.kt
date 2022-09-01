package no.nav.helsearbeidsgiver.inntektsmelding.api

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import java.time.LocalDateTime
import java.util.*

class InntektsmeldingRegistrertProducer(
    private val rapidsConnection: RapidsConnection
) {
    fun publish(request: InntektsmeldingRequest) {
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                "@event_name" to "inntektsmelding_inn",
                "@behov" to listOf("BrregLøser", "AaregLøser"),
                "@id" to UUID.randomUUID(),
                "@opprettet" to LocalDateTime.now(),
                "inntektsmelding" to request
            )
        )
        rapidsConnection.publish(request.fødselsnummer, packet.toJson())
        logger.info("Publiserte til kafka ${packet.toJson()}")
    }
}
