package no.nav.helsearbeidsgiver.inntektsmelding.joark

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.Avsender
import no.nav.helsearbeidsgiver.dokarkiv.domene.GjelderPerson
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Utils.convert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toPretty
import no.nav.helsearbeidsgiver.felles.loeser.ObjectRiver
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.LocalDate
import java.util.UUID
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding as InntektsmeldingV1

data class JournalfoerImMelding(
    val eventName: EventName,
    val transaksjonId: UUID,
    // TODO endre til v1.Inntektsmelding når kun den brukes
    val inntektsmeldingJson: JsonElement
)

class JournalfoerImRiver(
    private val dokArkivClient: DokArkivClient
) : ObjectRiver<JournalfoerImMelding>() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): JournalfoerImMelding? {
        val behovType = Key.BEHOV.lesOrNull(BehovType.serializer(), json)
        return if (
            setOf(Key.DATA, Key.FAIL).any(json::containsKey) ||
            (behovType != null && behovType != BehovType.JOURNALFOER)
        ) {
            null
        } else {
            val eventName = Key.EVENT_NAME.les(EventName.serializer(), json)
            val transaksjonId = Key.UUID.les(UuidSerializer, json)

            when (eventName) {
                EventName.INNTEKTSMELDING_MOTTATT ->
                    JournalfoerImMelding(
                        eventName = eventName,
                        transaksjonId = transaksjonId,
                        inntektsmeldingJson = Key.INNTEKTSMELDING_DOKUMENT.les(JsonElement.serializer(), json)
                    )

                EventName.AAPEN_IM_LAGRET ->
                    JournalfoerImMelding(
                        eventName = eventName,
                        transaksjonId = transaksjonId,
                        inntektsmeldingJson = Key.AAPEN_INNTEKTMELDING.les(JsonElement.serializer(), json)
                    )

                else ->
                    null
            }
        }
    }

    override fun JournalfoerImMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        "Mottok melding med event '$eventName'. Sender behov '${BehovType.LAGRE_JOURNALPOST_ID}'.".also {
            logger.info(it)
            sikkerLogger.info("$it Innkommende melding:\n${json.toPretty()}")
        }

        val inntektsmelding = runCatching {
            // Prøv ny IM-modell
            inntektsmeldingJson.fromJson(InntektsmeldingV1.serializer())
                .convert()
        }
            .getOrElse {
                // Fall tilbake til gammel IM-modell
                inntektsmeldingJson.fromJson(Inntektsmelding.serializer())
            }

        val journalpostId = opprettOgFerdigstillJournalpost(transaksjonId, inntektsmelding)

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.LAGRE_JOURNALPOST_ID.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.INNTEKTSMELDING_DOKUMENT to inntektsmeldingJson,
            Key.JOURNALPOST_ID to journalpostId.toJson(),
            Key.FORESPOERSEL_ID to json[Key.FORESPOERSEL_ID],
            Key.AAPEN_ID to json[Key.AAPEN_ID]
        )
            .mapValuesNotNull { it }
            .also {
                MdcUtils.withLogFields(
                    Log.behov(BehovType.LAGRE_JOURNALPOST_ID)
                ) {
                    logger.info("Publiserer behov '${BehovType.LAGRE_JOURNALPOST_ID}' med journalpost-ID '$journalpostId'.")
                    sikkerLogger.info("Publiserer behov:\n${it.toPretty()}")
                }
            }
    }

    override fun JournalfoerImMelding.haandterFeil(json: Map<Key, JsonElement>, error: Throwable): Map<Key, JsonElement> {
        val fail = Fail(
            feilmelding = "Klarte ikke journalføre.",
            event = eventName,
            transaksjonId = transaksjonId,
            forespoerselId = json[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer),
            utloesendeMelding = json.plus(
                Key.BEHOV to BehovType.JOURNALFOER.toJson()
            )
                .toJson()
        )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
            .plus(Key.AAPEN_ID to json[Key.AAPEN_ID])
            .mapValuesNotNull { it }
    }

    override fun JournalfoerImMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId)
        )

    private fun opprettOgFerdigstillJournalpost(transaksjonId: UUID, inntektsmelding: Inntektsmelding): String {
        "Prøver å opprette og ferdigstille journalpost.".also {
            logger.info(it)
            sikkerLogger.info("$it Gjelder IM:\n$inntektsmelding")
        }

        val response = Metrics.dokArkivRequest.recordTime(dokArkivClient::opprettOgFerdigstillJournalpost) {
            dokArkivClient.opprettOgFerdigstillJournalpost(
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
        }

        if (response.journalpostFerdigstilt) {
            logger.info("Opprettet og ferdigstilte journalpost med ID '${response.journalpostId}'.")
        } else {
            logger.error("Opprettet, men ferdigstilte ikke journalpost med ID '${response.journalpostId}'.")
        }

        return response.journalpostId
    }
}
