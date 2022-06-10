package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory

class JournalførInntektsmeldingLøser(rapidsConnection: RapidsConnection) : River.PacketListener {

    companion object {
        internal const val behov = "JournalførInntektsmeldingLøser"
    }

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val log = LoggerFactory.getLogger(this::class.java)

    init {
        River(rapidsConnection).apply {
            validate { it.requireValue("@event_name" , "inntektsmelding_inn") }
            validate { it.requireContains("@behov", behov) }
            validate { it.requireValue("@løsning" , "persistert") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("Skal journalføre: $packet")
        // TODO - Ferdigstill journalpost
        val journalpostID = "JP-123"
        //
        packet.setBehov(behov, packet)
        packet.setLøsning(journalpostID, packet)
        context.publish(packet.toJson())
    }

    private fun JsonMessage.setBehov(nøkkel: String, data: Any) {
        this["@behov"] = mapOf(
            nøkkel to data
        )
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
