@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.NotifikasjonLøsning
import org.slf4j.LoggerFactory

class NotifikasjonLøser(rapidsConnection: RapidsConnection) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val BEHOV = BehovType.NOTIFIKASJON

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll("@behov", BEHOV)
                it.requireKey("@id")
                it.requireKey("inntektsmelding")
                it.requireKey("identitetsnummer")
                it.requireKey("inntektsmelding_journalført")
                it.rejectKey("@løsning")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Løser behov $BEHOV med id ${packet["@id"].asText()}")
        val identitetsnummer = packet["identitetsnummer"].asText()
        sikkerlogg.info("Fant behov for: $identitetsnummer")
        try {
            packet.setLøsning(BEHOV, NotifikasjonLøsning("Ok"))
            context.publish(packet.toJson())
            sikkerlogg.info("Sendte notifikasjon for $identitetsnummer")
        } catch (ex: Exception) {
            packet.setLøsning(BEHOV, NotifikasjonLøsning(error = Feilmelding("Klarte ikke sende notifikasjon")))
            sikkerlogg.info("Det oppstod en feil ved sending til $identitetsnummer")
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
