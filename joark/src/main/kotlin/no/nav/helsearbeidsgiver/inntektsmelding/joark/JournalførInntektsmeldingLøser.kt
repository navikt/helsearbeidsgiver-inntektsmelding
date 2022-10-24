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
                it.requireKey("identitetsnummer")
                it.requireKey("orgnrUnderenhet")
                it.requireKey("inntektsmelding")
            }
        }.register(this)
    }

    fun opprettJournalpost(fnr: String, orgnr: String, data: String): String {
        sikkerlogg.info("Bruker inntektsinformasjon $data")
        if (fnr.equals("000")) {
            throw Exception("Ukjent feil")
        }
        return "jp-123"
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet["@id"].asText()
        logger.info("Løser behov $BEHOV med id $uuid")
        val fnr = packet["identitetsnummer"].asText()
        val orgnr = packet["orgnrUnderenhet"].asText()
        try {
            sikkerlogg.info("Skal journalføre for $fnr i $orgnr")
            val journalpostId = opprettJournalpost(fnr, orgnr, packet["inntektsmelding"].asText())
            packet.setLøsning(BEHOV, JournalpostLøsning(journalpostId))
            context.publish(packet.toJson())
        } catch (ex: Exception) {
            sikkerlogg.info("Klarte ikke journalføre for $fnr i $orgnr", ex)
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
        sikkerlogg.error("Fikk error $problems")
    }
}
