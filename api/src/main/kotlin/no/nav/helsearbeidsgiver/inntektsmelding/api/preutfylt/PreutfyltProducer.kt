package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import java.time.LocalDateTime
import java.util.UUID

class PreutfyltProducer(
    private val rapidsConnection: RapidsConnection
) {
    init {
        logger.info("Starter PreutfyltProducer...")
    }

    fun publish(request: PreutfyltRequest): String {
        val uuid = UUID.randomUUID()
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                "@event_name" to "preutfylt",
                "@behov" to listOf(
                    BehovType.VIRKSOMHET.name,
                    BehovType.FULLT_NAVN.name,
                    BehovType.INNTEKT.name,
                    BehovType.ARBEIDSFORHOLD.name,
                    BehovType.EGENMELDING.name,
                    BehovType.SYK.name
                ),
                "@id" to uuid,
                "@opprettet" to LocalDateTime.now(),
                "uuid" to uuid,
                Key.ORGNRUNDERENHET.str to request.orgnrUnderenhet,
                "identitetsnummer" to request.identitetsnummer
            )
        )
        rapidsConnection.publish(request.identitetsnummer, packet.toJson())
        logger.info("Publiserte til kafka id=$uuid")
        sikkerlogg.info("Publiserte til kafka id=$uuid json=${packet.toJson()}")
        return uuid.toString()
    }
}
