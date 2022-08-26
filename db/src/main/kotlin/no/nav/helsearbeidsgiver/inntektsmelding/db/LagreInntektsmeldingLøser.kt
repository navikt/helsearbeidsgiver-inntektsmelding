package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory

class LagreInntektsmeldingLøser(rapidsConnection: RapidsConnection) : River.PacketListener {

    companion object {
        internal const val behov = "LagreInntektsmeldingLøser"
    }

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val log = LoggerFactory.getLogger(this::class.java)

    init {
        River(rapidsConnection).apply {
            validate { it.requireValue("@event_name", "inntektsmelding_inn") }
            validate { it.requireContains("@behov", behov) }
            validate { it.rejectKey("@løsning") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("Skal lagre i db: ${packet.toJson()}")
        // TODO - Lagre inntektsmelding i databasen
        // Følgende publiser at inntektsmelding er persistert
        packet.setLøsning(behov, "persistert")
        context.publish(packet.toJson())
    }

    private fun JsonMessage.setLøsning(nøkkel: String, data: Any) {
        this["@løsning"] = mapOf(
            nøkkel to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error("Fikk error $problems")
    }
}
