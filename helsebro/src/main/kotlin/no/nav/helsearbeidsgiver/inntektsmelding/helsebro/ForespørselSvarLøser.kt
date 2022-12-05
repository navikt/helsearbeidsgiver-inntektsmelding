package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import no.nav.helse.rapids_rivers.*
import java.util.UUID

class ForespørselSvarLøser(rapid: RapidsConnection) : River.PacketListener {
    init {
        River(rapid).apply {
            validate {
                it.demandValue("eventType", "FORESPØRSEL_SVAR")
                it.requireKey(
                    "orgnr",
                    "fnr",
                    "vedtaksperiodeId",
                    "fom",
                    "tom",
                    "forespurtData"
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok melding om ${packet["eventType"].asText()}")
        loggerSikker.info("Mottok melding:\n${packet.toJson()}")

        val forespørselSvar = ForespørselSvar(
            orgnr = packet["orgnr"].asText(),
            fnr = packet["fnr"].asText(),
            vedtaksperiodeId = packet["vedtaksperiodeId"].asText().let(UUID::fromString),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate(),
            forespurtData = packet["forespurtData"].toString().let(Json::decodeFromString)
        )

        loggerSikker.info("Oversatte melding:\n$forespørselSvar")
    }
}
