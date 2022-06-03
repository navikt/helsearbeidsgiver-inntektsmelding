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
            "@behov" to "BrregLøser",
            "@event_name" to "inntektsmelding_registrert",
            "@id" to UUID.randomUUID(),
            "@opprettet" to LocalDateTime.now(),
        ))
        logger.info("Skal publisere til kafka")
        rapidsConnection.publish("Kode", packet.toJson())
        logger.info("Publiserte til kafka")
    }

}
