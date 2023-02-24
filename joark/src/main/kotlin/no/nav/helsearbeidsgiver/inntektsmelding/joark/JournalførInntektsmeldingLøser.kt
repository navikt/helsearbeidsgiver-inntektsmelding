@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark

import com.fasterxml.jackson.databind.JsonNode
import jdk.jfr.EventType
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivException
import no.nav.helsearbeidsgiver.felles.*
import no.nav.helsearbeidsgiver.felles.inntektsmelding.db.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class JournalførInntektsmeldingLøser(private val rapidsConnection: RapidsConnection, val dokarkivClient: DokArkivClient) : River.PacketListener {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue(Key.EVENT_NAME.str,EventName.INNTEKTSMELDING_MOTTATT.name)
                it.demandAll(Key.BEHOV.str, BehovType.JOURNALFOER)
                it.requireKey(Key.INNTEKTSMELDING_DOKUMENT.str)
                it.rejectKey(Key.LØSNING.str)
                it.interestedIn(Key.UUID.str)
            }
        }.register(this)
    }

    suspend fun opprettJournalpost(uuid: String, inntektsmelding: InntektsmeldingDokument): String {
        sikkerlogg.info("Bruker inntektsinformasjon $inntektsmelding")
        val request = mapOpprettJournalpostRequest(uuid, inntektsmelding, inntektsmelding.virksomhetNavn)
        return dokarkivClient.opprettJournalpost(request, false, "callId_$uuid").journalpostId
    }

//    fun sendNotifikasjon(uuid: String, inntektsmelding: InntektsmeldingDokument) {
//        val packet: JsonMessage = JsonMessage.newMessage(
//            mapOf(
//                Key.NOTIS.str to listOf(
//                    NotisType.NOTIFIKASJON.name
//                ),
//                Key.ID.str to uuid,
//                Key.OPPRETTET.str to LocalDateTime.now(),
//                Key.UUID.str to uuid,
//                Key.IDENTITETSNUMMER.str to inntektsmelding.identitetsnummer,
//                Key.ORGNRUNDERENHET.str to inntektsmelding.orgnrUnderenhet
//            )
//        )
//        rapidsConnection.publish(inntektsmelding.identitetsnummer, packet.toJson())
//    }

    fun mapInntektsmeldingDokument(jsonNode: JsonNode): InntektsmeldingDokument {
        try {
            return jsonNode.toJsonElement().fromJson()
        } catch (ex: Exception) {
            throw UgyldigFormatException(ex)
        }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet[Key.UUID.str].asText()
        logger.info("Løser behov" + BehovType.JOURNALFOER + " med id $uuid")
        sikkerlogg.info("Fikk pakke: ${packet.toJson()}")
        try {
            val inntektsmeldingDokument = mapInntektsmeldingDokument(packet[Key.INNTEKTSMELDING_DOKUMENT.str])
            sikkerlogg.info("Skal journalføre: $inntektsmeldingDokument")
            val journalpostId = runBlocking { opprettJournalpost(uuid, inntektsmeldingDokument) }
            sikkerlogg.info("Journalførte inntektsmeldingDokument journalpostid: $journalpostId")
            logger.info("Journalførte inntektsmeldingDokument med journalpostid: $journalpostId")
            val løsning = JournalpostLøsning(journalpostId)
            publiserLøsning(løsning, packet, context)
            val eventName = packet[Key.EVENT_NAME.str].asText()
            publiserLagring(uuid, journalpostId, inntektsmeldingDokument.identitetsnummer, eventName)
//            try {
//                sendNotifikasjon(uuid, inntektsmeldingDokument)
//                sikkerlogg.info("Registrere melding om notifikasjon for $inntektsmeldingDokument")
//            } catch (ex: Exception) {
//                sikkerlogg.error("Klarte ikke registrere melding om notifikasjon for $inntektsmeldingDokument", ex)
//            }
        } catch (ex: DokArkivException) {
            sikkerlogg.info("Klarte ikke journalføre", ex)
            publiserLøsning(JournalpostLøsning(error = Feilmelding("Kall mot dokarkiv feilet")), packet, context)
        } catch (ex: UgyldigFormatException) {
            sikkerlogg.info("Klarte ikke journalføre: feil format!", ex)
            publiserLøsning(JournalpostLøsning(error = Feilmelding("Feil format i InntektsmeldingDokument")), packet, context)
        } catch (ex: Exception) {
            sikkerlogg.info("Klarte ikke journalføre!", ex)
            JournalpostLøsning(error = Feilmelding("Klarte ikke journalføre"))
        }
    }

    fun publiserLagring(uuid: String, journalpostId: String, identitetsnummer: String, eventName: String) {
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to eventName,
                Key.BEHOV.str to listOf(BehovType.LAGRE_JOURNALPOST_ID),
                Key.OPPRETTET.str to LocalDateTime.now(),
                Key.JOURNALPOST_ID.str to journalpostId,
                Key.UUID.str to uuid
            )
        )
        rapidsConnection.publish(identitetsnummer, packet.toJson())
    }

    fun publiserLøsning(løsning: JournalpostLøsning, packet: JsonMessage, context: MessageContext) {
        packet.setLøsning(BehovType.JOURNALFOER, løsning)
        context.publish(packet.toJson())
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
    }

    internal class UgyldigFormatException(ex: Exception) : Exception("Klarte ikke lese ut Inntektsmelding fra Json node!", ex)
}
