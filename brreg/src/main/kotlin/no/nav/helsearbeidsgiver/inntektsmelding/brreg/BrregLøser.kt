package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.felles.Behov
import org.slf4j.LoggerFactory

class BrregLøser(rapidsConnection: RapidsConnection, private val brregClient: BrregClient) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val BEHOV = Behov.VIRKSOMHET.name

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll("@behov", listOf(BEHOV))
                it.requireKey("@id")
                it.requireKey("orgnrUnderenhet")
                it.rejectKey("@løsning")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Løser behov $BEHOV med id ${packet["@id"].asText()}")
        val løsning = brregClient.getVirksomhetsNavn(packet["orgnrUnderenhet"].asText())
        packet.setLøsning(BEHOV, løsning)
        context.publish(packet.toJson())
    }

    private fun JsonMessage.setLøsning(nøkkel: String, data: Any) {
        this["@løsning"] = mapOf(
            nøkkel to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
