package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory

class BrregLøser(rapidsConnection: RapidsConnection) : River.PacketListener {

    companion object {
        internal const val behov = "BrregLøser"
    }

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val log = LoggerFactory.getLogger(this::class.java)

    init {
        River(rapidsConnection).apply {
            // validate { it.forbid("@løsning") }
            validate { it.requireValue("@event_name" , "inntektsmelding_registrert") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("Mottok melding: ${packet.toJson()}")
        // Følgende re-publiserer tilbake
        // val fantDataFraBrreg = "NYTT" // Kall opp brreg her
        // packet.setLøsning(behov, fantDataFraBrreg)
        // context.publish(packet.toJson())
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
