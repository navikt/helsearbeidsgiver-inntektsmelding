package no.nav.helsearbeidsgiver.inntektsmelding.joark

import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.Avsender
import no.nav.helsearbeidsgiver.dokarkiv.domene.GjelderPerson
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.publishBehov
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.LocalDate
import java.util.UUID

// TODO slett etter overgangsperiode
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

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.EVENT_NAME.str, EventName.INNTEKTSMELDING_MOTTATT.name)
            it.demandValue(Key.BEHOV.str, JOURNALFOER_BEHOV.name)
            it.requireKey(Key.INNTEKTSMELDING_DOKUMENT.str)
        }
    }

    override fun onBehov(behov: Behov) {
        try {
            val json = behov.jsonMessage.toJson().parseJson().toMap()

            val transaksjonId = Key.UUID.les(UuidSerializer, json)
            val inntektsmelding = Key.INNTEKTSMELDING_DOKUMENT.les(Inntektsmelding.serializer(), json)

            logger.info("Løser behov '${BehovType.JOURNALFOER}' med transaksjonId $transaksjonId")
            sikkerLogger.info("Skal journalføre: $inntektsmelding")

            val journalpostId = opprettOgFerdigstillJournalpost(transaksjonId, inntektsmelding)

            sikkerLogger.info("Journalførte inntektsmelding journalpostid: $journalpostId")
            logger.info("Journalførte inntektsmelding med journalpostid: $journalpostId")

            rapidsConnection.publishBehov(
                eventName = behov.event,
                behovType = BehovType.LAGRE_JOURNALPOST_ID,
                transaksjonId = transaksjonId,
                forespoerselId = behov.forespoerselId?.let(UUID::fromString),
                Key.JOURNALPOST_ID to journalpostId.toJson()
            )
        } catch (ex: Exception) {
            sikkerLogger.error("Klarte ikke journalføre!", ex)
            publishFail(
                behov.createFail("Klarte ikke journalføre")
            )
        }
    }

    private fun opprettOgFerdigstillJournalpost(transaksjonId: UUID, inntektsmelding: Inntektsmelding): String {
        sikkerLogger.info("Bruker inntektsinformasjon $inntektsmelding")

        logger.info("Prøver å opprette og ferdigstille journalpost for $transaksjonId...")
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
                dokumenter = tilDokumenter(transaksjonId, inntektsmelding),
                eksternReferanseId = "ARI-$transaksjonId",
                callId = "callId_$transaksjonId"
            )
        }.also {
            requestTimer.observeDuration()
        }

        if (response.journalpostFerdigstilt) {
            logger.info("Opprettet og ferdigstilte journalpost ${response.journalpostId} for $transaksjonId.")
        }

        return response.journalpostId
    }
}
