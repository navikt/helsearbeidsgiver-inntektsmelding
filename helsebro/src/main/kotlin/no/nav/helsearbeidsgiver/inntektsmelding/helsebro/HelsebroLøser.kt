package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.UUID

class HelsebroLøser(
    rapidsConnection: RapidsConnection,
    private val priProducer: PriProducer
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("eventType", "FORESPØRSEL_MOTTATT")
                it.requireKey(
                    "orgnr",
                    "fnr",
                    "vedtaksperiodeId"
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok melding om ${packet["eventType"].asText()}")
        loggerSikker.info("Mottok melding:\n${packet.toJson()}")

        val trengerForespurtData = TrengerForespurtData(
            orgnr = packet["orgnr"].asText(),
            fnr = packet["fnr"].asText(),
            vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText())
        )
        priProducer.send(trengerForespurtData)

        logger.info("Publiserte melding om ${trengerForespurtData.eventType}")
        loggerSikker.info("Publiserte melding:\n${trengerForespurtData.toJson()}")
    }
}
