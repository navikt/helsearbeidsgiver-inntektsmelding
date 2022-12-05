package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.UUID

/**
 * Tar imot notifikasjon om at det er kommet en forespørsel om arbeidsgiveropplysninger.
 * Skal i fremtiden trigge opprettelse av arbeidsgiver-notifikasjon om at vi trenger opplysninger.
 */
class ForespørselMottattLøser(
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

        val trengerForespørsel = TrengerForespørsel(
            orgnr = packet["orgnr"].asText(),
            fnr = packet["fnr"].asText(),
            vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText())
        )
        priProducer.send(trengerForespørsel)

        logger.info("Publiserte melding om ${trengerForespørsel.eventType}")
        loggerSikker.info("Publiserte melding:\n${trengerForespørsel.toJson()}")
    }
}
