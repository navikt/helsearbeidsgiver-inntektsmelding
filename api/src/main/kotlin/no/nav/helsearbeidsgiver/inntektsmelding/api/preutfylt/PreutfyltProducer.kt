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
                Key.EVENT_NAME.str to "preutfylt",
                Key.BEHOV.str to listOf(
                    BehovType.VIRKSOMHET.name,
                    BehovType.FULLT_NAVN.name,
                    BehovType.INNTEKT.name,
                    BehovType.ARBEIDSFORHOLD.name
                ),
                Key.OPPRETTET.str to LocalDateTime.now(),
                Key.UUID.str to uuid,
                Key.ORGNRUNDERENHET.str to request.orgnrUnderenhet,
                Key.IDENTITETSNUMMER.str to request.identitetsnummer
            )
        )
        rapidsConnection.publish(request.identitetsnummer, packet.toJson())
        logger.info("Publiserte preutfylt behov id=$uuid")
        sikkerlogg.info("Publiserte preutfylt behov id=$uuid json=${packet.toJson()}")
        return uuid.toString()
    }
}
