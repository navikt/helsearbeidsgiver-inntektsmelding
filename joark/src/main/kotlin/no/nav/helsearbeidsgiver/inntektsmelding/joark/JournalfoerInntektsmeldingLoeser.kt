package no.nav.helsearbeidsgiver.inntektsmelding.joark

import com.fasterxml.jackson.databind.JsonNode
import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.Avsender
import no.nav.helsearbeidsgiver.dokarkiv.domene.GjelderPerson
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.LocalDate
import java.time.LocalDateTime

class JournalfoerInntektsmeldingLoeser(
    rapidsConnection: RapidsConnection,
    private val dokarkivClient: DokArkivClient
) : Loeser(rapidsConnection) {

    private val JOURNALFOER_BEHOV = BehovType.JOURNALFOER
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()
    private val requestLatency = Summary.build()
        .name("simba_joark_journalfoer_im_latency_seconds")
        .help("journaloer inntektsmelding latency in seconds")
        .register()

    private fun mapInntektsmeldingDokument(jsonNode: JsonNode): Inntektsmelding {
        try {
            return jsonNode.toString().fromJson(Inntektsmelding.serializer())
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

    override fun onBehov(behov: Behov) {
        logger.info("Løser behov " + BehovType.JOURNALFOER + " med uuid ${behov.uuid()}")
        var inntektsmelding: Inntektsmelding? = null
        try {
            inntektsmelding = mapInntektsmeldingDokument(behov[Key.INNTEKTSMELDING_DOKUMENT])
            sikkerLogger.info("Skal journalføre: $inntektsmelding")
            val journalpostId = opprettOgFerdigstillJournalpost(behov.uuid(), inntektsmelding)
            sikkerLogger.info("Journalførte inntektsmelding journalpostid: $journalpostId")
            logger.info("Journalførte inntektsmelding med journalpostid: $journalpostId")
            behov.createBehov(
                BehovType.LAGRE_JOURNALPOST_ID,
                mapOf(
                    Key.OPPRETTET to LocalDateTime.now(),
                    Key.JOURNALPOST_ID to journalpostId
                )
            )
                .also { publishBehov(it) }
        } catch (ex: UgyldigFormatException) {
            sikkerLogger.error("Klarte ikke journalføre: feil format!", ex)
            publishFail(
                behov.createFail("Feil format i Inntektsmelding")
            )
        } catch (ex: Exception) {
            sikkerLogger.error("Klarte ikke journalføre!", ex)
            publishFail(
                behov.createFail("Klarte ikke journalføre")
            )
        }
    }

    private fun opprettOgFerdigstillJournalpost(uuid: String, inntektsmelding: Inntektsmelding): String {
        sikkerLogger.info("Bruker inntektsinformasjon $inntektsmelding")

        logger.info("Prøver å opprette og ferdigstille journalpost for $uuid...")
        val requestTimer = requestLatency.startTimer()
        val response = runBlocking {
            dokarkivClient.opprettOgFerdigstillJournalpost(
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
        }.also {
            requestTimer.observeDuration()
        }

        if (response.journalpostFerdigstilt) {
            logger.info("Opprettet og ferdigstilte journalpost ${response.journalpostId} for $uuid.")
        }

        return response.journalpostId
    }
}

private class UgyldigFormatException(ex: Exception) : Exception("Klarte ikke lese ut Inntektsmelding fra Json node!", ex)
