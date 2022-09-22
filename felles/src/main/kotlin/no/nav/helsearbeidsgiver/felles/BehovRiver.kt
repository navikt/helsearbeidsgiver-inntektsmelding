package no.nav.helsearbeidsgiver.felles

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory

class BehovRiver(
    rapidsConnection: RapidsConnection,
    private val behov: String,
    private val validation: (JsonMessage) -> Unit = {},
    private val packetListener: (JsonMessage, MessageContext) -> Unit
) : River.PacketValidation, River.PacketListener {
    private companion object {
        private val logger = LoggerFactory.getLogger(BehovRiver::class.java)
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection).apply {
            validate(this@BehovRiver)
            validate(validation)
        }.register(this)
    }

    override fun validate(message: JsonMessage) {
        message.demandAll("@behov", listOf(behov))
        message.rejectKey("@final", "@løsning")
        message.requireKey("identitesnummer", "orgnrUnderenhet")
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logger.error("Forstod ikke $behov (se sikkerLog for detaljer)")
        sikkerLogg.error("Forstod ikke $behov: ${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        packetListener(packet, context)
    }
}
