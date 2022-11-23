@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivException
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.JournalpostLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument.UgyldigFormatException
import no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument.mapInntektsmelding
import org.slf4j.LoggerFactory

class JournalførInntektsmeldingLøser(rapidsConnection: RapidsConnection, val dokarkivClient: DokArkivClient) : River.PacketListener {

    private val BEHOV = BehovType.JOURNALFOER
    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll("@behov", BEHOV)
                it.requireKey("@id")
                it.rejectKey("@løsning")
                it.interestedIn("inntektsmelding")
                it.interestedIn("session")
                it.interestedIn("uuid")
            }
        }.register(this)
    }

    suspend fun opprettJournalpost(uuid: String, inntektsmelding: InntektsmeldingDokument): String {
        sikkerlogg.info("Bruker inntektsinformasjon $inntektsmelding")
        val request = mapOpprettJournalpostRequest(uuid, inntektsmelding, inntektsmelding.virksomhetNavn)
        return dokarkivClient.opprettJournalpost(request, false, "callId_$uuid").journalpostId
    }

    fun hentArbeidsgiver(session: JsonNode): String {
        return session.get(BehovType.VIRKSOMHET.name)?.get("value")?.asText() ?: "Ukjent"
    }

    fun hentNavn(session: JsonNode): String {
        return session.get(BehovType.FULLT_NAVN.name)?.get("value")?.asText() ?: "Ukjent"
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet[Key.UUID.str].asText()
        logger.info("Løser behov $BEHOV med id $uuid")
        sikkerlogg.info("Fikk pakke: ${packet.toJson()}")
        var løsning = JournalpostLøsning(error = Feilmelding("Klarte ikke journalføre"))
        val session = packet[Key.SESSION.str]
        sikkerlogg.info("Fant session: $session")
        try {
            val inntektsmelding = mapInntektsmelding(packet["inntektsmelding"])
            inntektsmelding.virksomhetNavn = hentArbeidsgiver(session)
            sikkerlogg.info("Fant virksomhetNavn: ${inntektsmelding.virksomhetNavn}")
            inntektsmelding.fulltNavn = hentNavn(session)
            sikkerlogg.info("Fant fulltNavn: ${inntektsmelding.fulltNavn}")
            sikkerlogg.info("Skal journalføre: $inntektsmelding")
            val journalpostId = runBlocking { opprettJournalpost(uuid, inntektsmelding) }
            sikkerlogg.info("Journalførte inntektsmelding med journalpostid: $journalpostId")
            logger.info("Journalførte inntektsmelding med journalpostid: $journalpostId")
            løsning = JournalpostLøsning(journalpostId)
        } catch (ex: DokArkivException) {
            sikkerlogg.info("Klarte ikke journalføre", ex)
            løsning = JournalpostLøsning(error = Feilmelding("Kall mot dokarkiv feilet"))
        } catch (ex: UgyldigFormatException) {
            sikkerlogg.info("Klarte ikke journalføre: feil format!", ex)
            løsning = JournalpostLøsning(error = Feilmelding("Feil format i inntektsmelding"))
        } catch (ex: Exception) {
            sikkerlogg.info("Klarte ikke journalføre!", ex)
        } finally {
            packet.setLøsning(BEHOV, løsning)
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
