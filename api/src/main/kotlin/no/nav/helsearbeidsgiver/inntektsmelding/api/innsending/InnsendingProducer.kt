package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
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
                Key.EVENT_NAME.str to "inntektsmelding",
                Key.BEHOV.str to listOf(
                    BehovType.VIRKSOMHET.name,
                    BehovType.ARBEIDSFORHOLD.name,
                    BehovType.NOTIFIKASJON.name,
                ),
                Key.NESTE_BEHOV.str to listOf(
                    BehovType.JOURNALFOER.name
                ),
                Key.ID.str to uuid,
                Key.OPPRETTET.str to LocalDateTime.now(),
                "uuid" to uuid,
                "orgnrUnderenhet" to request.orgnrUnderenhet,
                "identitetsnummer" to request.identitetsnummer,
                "inntektsmelding" to request
            )
        )
        rapidsConnection.publish(request.identitetsnummer, packet.toJson())
        logger.info("Publiserte til kafka id=$uuid")
        sikkerlogg.info("Publiserte til kafka id=$uuid json=${packet.toJson()}")
        return uuid.toString()
    }
}
