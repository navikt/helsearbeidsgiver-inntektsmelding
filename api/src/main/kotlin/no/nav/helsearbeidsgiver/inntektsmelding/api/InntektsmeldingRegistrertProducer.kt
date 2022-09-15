package no.nav.helsearbeidsgiver.inntektsmelding.api

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import java.time.LocalDateTime
import java.util.*

class InntektsmeldingRegistrertProducer(
    private val rapidsConnection: RapidsConnection
) {
    fun publish(request: InntektsmeldingRequest): String {
        val uuid = UUID.randomUUID()
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                "@event_name" to "inntektsmelding_inn",
                "@behov" to listOf("BrregLøser", "PdlLøser"),
                "@id" to uuid,
                "@opprettet" to LocalDateTime.now(),
                "uuid" to uuid,
                "inntektsmelding" to request,
                "orgnrUnderenhet" to request.orgnrUnderenhet,
                "identitetsnummer" to request.identitetsnummer,
            )
        )
        rapidsConnection.publish(request.identitetsnummer, packet.toJson())
        logger.info("Publiserte til kafka id=$uuid")
        sikkerlogg.info("Publiserte til kafka id=$uuid json=${packet.toJson()}")
        return uuid.toString()
    }
}
