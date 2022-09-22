package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.Behov
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import java.time.LocalDateTime
import java.util.*

class PreutfyltProducer(
    private val rapidsConnection: RapidsConnection
) {
    fun publish(request: PreutfyllRequest): String {
        val uuid = UUID.randomUUID()
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                "@event_name" to "preutfylt",
                "@behov" to listOf(Behov.VIRKSOMHET.name, Behov.FULLT_NAVN.name),
                "@id" to uuid,
                "@opprettet" to LocalDateTime.now(),
                "uuid" to uuid,
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
