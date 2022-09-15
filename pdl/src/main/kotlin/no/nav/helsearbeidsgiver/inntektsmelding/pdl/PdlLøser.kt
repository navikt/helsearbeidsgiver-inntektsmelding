package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory

class PdlLøser(
    rapidsConnection: RapidsConnection
    // private val pdlClient: PdlClient
) : River.PacketListener {

    companion object {
        internal const val behov = "PdlLøser"
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll("@behov", listOf(behov))
                it.requireKey("@id")
                it.requireKey("inntektsmelding.identitetsnummer")
                it.rejectKey("@løsning")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Løser behov $behov med id ${packet["@id"].asText()}")
        // val navn = pdlClient.personNavn(packet["identitetsnummer"].asText())?.navn?.firstOrNull()
        // val løsning = "${navn?.fornavn} ${navn?.mellomnavn} ${navn?.etternavn}"
        val løsning = "Navn Navnesen"
        packet.setLøsning(behov, løsning)
        context.publish(packet.toJson())
    }

    private fun JsonMessage.setLøsning(nøkkel: String, data: Any) {
        this["@løsning"] = mapOf(
            nøkkel to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
