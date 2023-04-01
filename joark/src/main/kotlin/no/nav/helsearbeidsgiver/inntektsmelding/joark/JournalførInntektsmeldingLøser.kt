@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivException
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.JournalpostLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class JournalførInntektsmeldingLøser(rapidsConnection: RapidsConnection, val dokarkivClient: DokArkivClient) : Løser(rapidsConnection) {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun opprettJournalpost(uuid: String, inntektsmelding: InntektsmeldingDokument): String {
        sikkerlogg.info("Bruker inntektsinformasjon $inntektsmelding")
        val request = mapOpprettJournalpostRequest(uuid, inntektsmelding, inntektsmelding.virksomhetNavn)
        logger.info("Skal ferdigstille journalpost for $uuid...")
        val journalpostId = dokarkivClient.opprettJournalpost(request, true, "callId_$uuid").journalpostId
        logger.info("Fikk opprettet journalpost $journalpostId for $uuid")
        return journalpostId
    }

    fun mapInntektsmeldingDokument(jsonNode: JsonNode): InntektsmeldingDokument {
        try {
            return customObjectMapper().treeToValue<InntektsmeldingDokument>(jsonNode, InntektsmeldingDokument::class.java)
        } catch (ex: Exception) {
            throw UgyldigFormatException(ex)
        }
    }

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.EVENT_NAME.str, EventName.INNTEKTSMELDING_MOTTATT.name)
            it.demandValue(Key.BEHOV.str, BehovType.JOURNALFOER.name)
            it.requireKey(Key.INNTEKTSMELDING_DOKUMENT.str)
        }
    }

    override fun onBehov(packet: JsonMessage) {
        val uuid = packet[Key.UUID.str].asText()
        logger.info("Løser behov " + BehovType.JOURNALFOER + " med uuid $uuid")
        sikkerlogg.info("Fikk pakke: ${packet.toJson()}")
        try {
            val inntektsmeldingDokument = mapInntektsmeldingDokument(packet[Key.INNTEKTSMELDING_DOKUMENT.str])
            sikkerlogg.info("Skal journalføre: $inntektsmeldingDokument")
            val journalpostId = runBlocking { opprettJournalpost(uuid, inntektsmeldingDokument) }
            sikkerlogg.info("Journalførte inntektsmeldingDokument journalpostid: $journalpostId")
            logger.info("Journalførte inntektsmeldingDokument med journalpostid: $journalpostId")
            val løsning = JournalpostLøsning(journalpostId)
            publiserLøsning(løsning, packet)
            val eventName = packet[Key.EVENT_NAME.str].asText()
            publiserLagring(uuid, journalpostId, inntektsmeldingDokument.identitetsnummer, eventName)
        } catch (ex: DokArkivException) {
            sikkerlogg.error("Klarte ikke journalføre", ex)
            publiserLøsning(JournalpostLøsning(error = Feilmelding("Kall mot dokarkiv feilet")), packet)
        } catch (ex: UgyldigFormatException) {
            sikkerlogg.error("Klarte ikke journalføre: feil format!", ex)
            publiserLøsning(JournalpostLøsning(error = Feilmelding("Feil format i InntektsmeldingDokument")), packet)
        } catch (ex: Exception) {
            sikkerlogg.error("Klarte ikke journalføre!", ex)
            publiserLøsning(JournalpostLøsning(error = Feilmelding("Klarte ikke journalføre")), packet)
        }
    }

    fun publiserLagring(uuid: String, journalpostId: String, identitetsnummer: String, eventName: String) {
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to eventName,
                Key.BEHOV.str to BehovType.LAGRE_JOURNALPOST_ID.name,
                Key.OPPRETTET.str to LocalDateTime.now(),
                Key.JOURNALPOST_ID.str to journalpostId,
                Key.UUID.str to uuid
            )
        )

        publishEvent(packet)
    }

    fun publiserLøsning(løsning: JournalpostLøsning, packet: JsonMessage) {
        packet.setLøsning(BehovType.JOURNALFOER, løsning)
        publishBehov(packet)
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }

    internal class UgyldigFormatException(ex: Exception) : Exception("Klarte ikke lese ut Inntektsmelding fra Json node!", ex)
}
