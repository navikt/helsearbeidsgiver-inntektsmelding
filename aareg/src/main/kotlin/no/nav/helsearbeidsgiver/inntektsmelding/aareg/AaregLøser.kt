@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Løsning
import org.slf4j.LoggerFactory

class AaregLøser(rapidsConnection: RapidsConnection) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val BEHOV = BehovType.ARBEIDSFORHOLD

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll("@behov", BEHOV)
                it.requireKey("@id")
                it.requireKey("identitetsnummer")
                it.rejectKey("@løsning")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Løser behov $BEHOV med id ${packet["@id"].asText()}")
        val fnr = packet["identitetsnummer"].asText()
        try {
            val arbeidsforhold = listOf(
                Arbeidsforhold("af-1", "Norge AS", 80f),
                Arbeidsforhold("af-2", "Norge AS", 20f)
            )
            packet.setLøsning(BEHOV, Løsning(BEHOV, arbeidsforhold))
            context.publish(packet.toJson())
            sikkerlogg.info("Fant arbeidsforhold $arbeidsforhold for $fnr")
        } catch (ex: Exception) {
            packet.setLøsning(BEHOV, Løsning(BEHOV, error = Feilmelding("Klarte ikke hente arbeidsforhold")))
            sikkerlogg.info("Det oppstod en feil ved henting av arbeidsforhold for $fnr")
            sikkerlogg.error(ex.stackTraceToString())
            context.publish(packet.toJson())
        }
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this["@løsning"] = mapOf(
            nøkkel.name to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
