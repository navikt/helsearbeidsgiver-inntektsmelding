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
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.createFail
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.utils.mapOfNotNull
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class JournalførInntektsmeldingLøser(rapidsConnection: RapidsConnection, val dokarkivClient: DokArkivClient) : Løser(rapidsConnection) {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val JOURNALFOER_BEHOV = BehovType.JOURNALFOER

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
            return customObjectMapper().treeToValue(jsonNode, InntektsmeldingDokument::class.java)
        } catch (ex: Exception) {
            throw UgyldigFormatException(ex)
        }
    }

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.EVENT_NAME.str, EventName.INNTEKTSMELDING_MOTTATT.name)
            it.demandValue(Key.BEHOV.str, JOURNALFOER_BEHOV.name)
            it.requireKey(Key.INNTEKTSMELDING_DOKUMENT.str)
        }
    }

    override fun onBehov(packet: JsonMessage) {
        val uuid = packet[Key.UUID.str].asText()
        logger.info("Løser behov " + BehovType.JOURNALFOER + " med uuid $uuid")
        sikkerlogg.info("Fikk pakke: ${packet.toJson()}")
        var inntektsmeldingDokument: InntektsmeldingDokument? = null
        try {
            inntektsmeldingDokument = mapInntektsmeldingDokument(packet[Key.INNTEKTSMELDING_DOKUMENT.str])
            sikkerlogg.info("Skal journalføre: $inntektsmeldingDokument")
            val journalpostId = runBlocking { opprettJournalpost(uuid, inntektsmeldingDokument) }
            sikkerlogg.info("Journalførte inntektsmeldingDokument journalpostid: $journalpostId")
            logger.info("Journalførte inntektsmeldingDokument med journalpostid: $journalpostId")
            publiserLagring(uuid, journalpostId)
        } catch (ex: DokArkivException) {
            sikkerlogg.error("Klarte ikke journalføre", ex)
            val data = mapOfNotNull(DataFelt.INNTEKTSMELDING_DOKUMENT to inntektsmeldingDokument)
            publishFail(packet.createFail("Kall mot dokarkiv feilet", data, behoveType = JOURNALFOER_BEHOV))
        } catch (ex: UgyldigFormatException) {
            sikkerlogg.error("Klarte ikke journalføre: feil format!", ex)
            val data = mapOfNotNull(DataFelt.INNTEKTSMELDING_DOKUMENT to inntektsmeldingDokument)
            publishFail(packet.createFail("Feil format i InntektsmeldingDokument", data, behoveType = JOURNALFOER_BEHOV))
        } catch (ex: Exception) {
            sikkerlogg.error("Klarte ikke journalføre!", ex)
            val data = mapOfNotNull(DataFelt.INNTEKTSMELDING_DOKUMENT to inntektsmeldingDokument)
            publishFail(packet.createFail("Klarte ikke journalføre", data, behoveType = JOURNALFOER_BEHOV))
        }
    }

    fun publiserLagring(uuid: String, journalpostId: String) {
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.BEHOV.str to BehovType.LAGRE_JOURNALPOST_ID.name,
                Key.OPPRETTET.str to LocalDateTime.now(),
                Key.JOURNALPOST_ID.str to journalpostId,
                Key.UUID.str to uuid
            )
        )

        publishBehov(packet)
    }

    internal class UgyldigFormatException(ex: Exception) : Exception("Klarte ikke lese ut Inntektsmelding fra Json node!", ex)
}
