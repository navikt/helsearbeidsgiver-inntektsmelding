package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

class HelsebroLøser(rapidsConnection: RapidsConnection) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                loggerSikker.info("validerer pakke: \n${it.toJson()}")
                it.demandValue("eventType", "FORESPØRSEL_MOTTATT")
                it.requireKey(
                    "orgnr",
                    "fnr"
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok melding.")
        loggerSikker.info("Mottok melding:\n${packet.toJson()}")
    }
}
