package no.nav.helsearbeidsgiver.inntektsmelding.joark

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.Avsender
import no.nav.helsearbeidsgiver.dokarkiv.domene.GjelderPerson
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.createFail
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.felles.utils.mapOfNotNull
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.LocalDate
import java.time.LocalDateTime

class JournalfoerInntektsmeldingLoeser(
    rapidsConnection: RapidsConnection,
    private val dokarkivClient: DokArkivClient
) : Løser(rapidsConnection) {

    private val JOURNALFOER_BEHOV = BehovType.JOURNALFOER
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    private fun mapInntektsmeldingDokument(jsonNode: JsonNode): InntektsmeldingDokument {
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
            it.requireKey(DataFelt.INNTEKTSMELDING_DOKUMENT.str)
        }
    }

    override fun onBehov(packet: JsonMessage) {
        val uuid = packet[Key.UUID.str].asText()
        logger.info("Løser behov " + BehovType.JOURNALFOER + " med uuid $uuid")
        sikkerLogger.info("Fikk pakke:\n${packet.toPretty()}")
        var inntektsmeldingDokument: InntektsmeldingDokument? = null
        try {
            inntektsmeldingDokument = mapInntektsmeldingDokument(packet[DataFelt.INNTEKTSMELDING_DOKUMENT.str])
            sikkerLogger.info("Skal journalføre: $inntektsmeldingDokument")
            val journalpostId = opprettOgFerdigstillJournalpost(uuid, inntektsmeldingDokument)
            sikkerLogger.info("Journalførte inntektsmeldingDokument journalpostid: $journalpostId")
            logger.info("Journalførte inntektsmeldingDokument med journalpostid: $journalpostId")
            publiserLagring(uuid, journalpostId)
        } catch (ex: UgyldigFormatException) {
            sikkerLogger.error("Klarte ikke journalføre: feil format!", ex)
            val data = mapOfNotNull(DataFelt.INNTEKTSMELDING_DOKUMENT to inntektsmeldingDokument)
            publishFail(packet.createFail("Feil format i InntektsmeldingDokument", data, behovType = JOURNALFOER_BEHOV))
        } catch (ex: Exception) {
            sikkerLogger.error("Klarte ikke journalføre!", ex)
            val data = mapOfNotNull(DataFelt.INNTEKTSMELDING_DOKUMENT to inntektsmeldingDokument)
            publishFail(packet.createFail("Klarte ikke journalføre", data, behovType = JOURNALFOER_BEHOV))
        }
    }

    private fun opprettOgFerdigstillJournalpost(uuid: String, inntektsmelding: InntektsmeldingDokument): String {
        sikkerLogger.info("Bruker inntektsinformasjon $inntektsmelding")

        logger.info("Prøver å opprette og ferdigstille journalpost for $uuid...")

        val response = runBlocking {
            dokarkivClient.opprettOgFerdigstillJournalpost(
                behandlingsTema = "ab0326",
                tittel = "Inntektsmelding",
                gjelderPerson = GjelderPerson(inntektsmelding.identitetsnummer),
                avsender = Avsender.Organisasjon(
                    orgnr = inntektsmelding.orgnrUnderenhet,
                    navn = inntektsmelding.virksomhetNavn
                ),
                datoMottatt = LocalDate.now(),
                dokumenter = tilDokumenter(uuid, inntektsmelding),
                eksternReferanseId = "ARI-$uuid",
                callId = "callId_$uuid"
            )
        }

        if (response.journalpostFerdigstilt) {
            logger.info("Opprettet og ferdigstilte journalpost ${response.journalpostId} for $uuid.")
        }

        return response.journalpostId
    }

    private fun publiserLagring(uuid: String, journalpostId: String) {
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
}

private class UgyldigFormatException(ex: Exception) : Exception("Klarte ikke lese ut Inntektsmelding fra Json node!", ex)
