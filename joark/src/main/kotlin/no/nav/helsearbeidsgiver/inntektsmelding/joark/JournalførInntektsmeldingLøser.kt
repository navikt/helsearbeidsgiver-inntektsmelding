@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.JournalpostLøsning
import org.slf4j.LoggerFactory

class JournalførInntektsmeldingLøser(rapidsConnection: RapidsConnection) : River.PacketListener {

    private val BEHOV = BehovType.JOURNALFOER
    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll("@behov", BEHOV)
                it.requireKey("@id")
                it.rejectKey("@løsning")
                it.requireKey("inntektsmelding")
            }
        }.register(this)
    }

    fun opprettJournalpost(data: Inntektsmelding): String {
        sikkerlogg.info("Bruker inntektsinformasjon $data")
        if (data.identitetsnummer.equals("000")) {
            throw Exception("Ukjent feil")
        }
        return "jp-123"
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet["@id"].asText()
        logger.info("Løser behov $BEHOV med id $uuid")
        try {
            val inntektsmelding = mapInntektsmelding(packet["inntektsmelding"])
            sikkerlogg.info("Skal journalføre $inntektsmelding")
            val journalpostId = opprettJournalpost(inntektsmelding)
            packet.setLøsning(BEHOV, JournalpostLøsning(journalpostId))
            context.publish(packet.toJson())
        } catch (ex2: UgyldigFormatException) {
            sikkerlogg.info("Klarte ikke journalføre: feil format!", ex2)
            packet.setLøsning(BEHOV, JournalpostLøsning(error = Feilmelding("Feil format i inntektsmelding")))
            context.publish(packet.toJson())
        } catch (ex: Exception) {
            sikkerlogg.info("Klarte ikke journalføre!", ex)
            packet.setLøsning(BEHOV, JournalpostLøsning(error = Feilmelding("Klarte ikke journalføre")))
            context.publish(packet.toJson())
        }
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this["@løsning"] = mapOf(
            nøkkel.name to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
    }
}
