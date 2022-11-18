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

    suspend fun opprettJournalpost(uuid: String, inntektsmelding: Inntektsmelding, arbeidsgiverNavn: String): String {
        sikkerlogg.info("Bruker inntektsinformasjon $inntektsmelding")
        val request = mapOpprettJournalpostRequest(uuid, inntektsmelding, arbeidsgiverNavn)
        return dokarkivClient.opprettJournalpost(request, true, "callId_$uuid").journalpostId
    }

    fun hentArbeidsgiver(session: JsonNode): String {
        return session.get(BehovType.VIRKSOMHET.name)?.get("value")?.asText() ?: "Ukjent"
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet[Key.UUID.str].asText()
        logger.info("Løser behov $BEHOV med id $uuid")
        sikkerlogg.info("Fikk pakke: ${packet.toJson()}")
        var løsning = JournalpostLøsning(error = Feilmelding("Klarte ikke journalføre"))
        val session = packet[Key.SESSION.str]
        val arbeidsgiverNavn = hentArbeidsgiver(session)
        try {
            val inntektsmelding = mapInntektsmelding(packet["inntektsmelding"])
            sikkerlogg.info("Fant session: $session")
            sikkerlogg.info("Skal journalføre: $inntektsmelding")
            val journalpostId = runBlocking { opprettJournalpost(uuid, inntektsmelding, arbeidsgiverNavn) }
            // TODO Lag kvittering til Altinn?
            // TODO Lag innboks melding i NAV?
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
