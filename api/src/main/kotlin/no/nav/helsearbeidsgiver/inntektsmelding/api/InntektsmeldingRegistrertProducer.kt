package no.nav.helsearbeidsgiver.inntektsmelding.api

import java.time.LocalDateTime
import java.util.*
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection

class InntektsmeldingRegistrertProducer(
    private val rapidsConnection: RapidsConnection
) {
    fun publish(request: InntektsmeldingRequest) {
        val packet: JsonMessage = JsonMessage.newMessage(mapOf(
            "@event_name" to "inntektsmelding_registrert",
            "@id" to UUID.randomUUID(),
            "@opprettet" to LocalDateTime.now(),
            "fødselsnummer" to request.fnr
        ))
        rapidsConnection.publish(request.fnr, packet.toJson())
        logger.info("Publiserte til kafka ${packet.toJson()}")
    }
}
