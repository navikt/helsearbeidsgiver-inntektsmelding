package no.nav.helsearbeidsgiver.inntektsmelding.joark

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.Avsender
import no.nav.helsearbeidsgiver.dokarkiv.domene.GjelderPerson
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.json.toPretty
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.LocalDate
import java.util.UUID

data class JournalfoerImMelding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val inntektsmelding: Inntektsmelding,
)

class JournalfoerImRiver(
    private val dokArkivClient: DokArkivClient,
) : ObjectRiver<JournalfoerImMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): JournalfoerImMelding? =
        if (setOf(Key.BEHOV, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()
            val eventName = Key.EVENT_NAME.les(EventName.serializer(), json)
            val transaksjonId = Key.KONTEKST_ID.les(UuidSerializer, json)

            val inntektsmelding =
                when (eventName) {
                    EventName.INNTEKTSMELDING_MOTTATT -> Key.INNTEKTSMELDING.les(Inntektsmelding.serializer(), data)
                    EventName.SELVBESTEMT_IM_LAGRET -> Key.SELVBESTEMT_INNTEKTSMELDING.les(Inntektsmelding.serializer(), data)
                    else -> null
                }

            if (inntektsmelding == null) {
                null
            } else {
                JournalfoerImMelding(
                    eventName = eventName,
                    transaksjonId = transaksjonId,
                    inntektsmelding = inntektsmelding,
                )
            }
        }

    override fun JournalfoerImMelding.bestemNoekkel(): KafkaKey = KafkaKey(inntektsmelding.type.id)

    override fun JournalfoerImMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        "Mottok melding med event '$eventName'.".also {
            logger.info(it)
            sikkerLogger.info("$it Innkommende melding:\n${json.toPretty()}")
        }

        val journalpostId = opprettOgFerdigstillJournalpost(transaksjonId, inntektsmelding)

        return mapOf(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_JOURNALFOERT.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(),
            Key.JOURNALPOST_ID to journalpostId.toJson(),
            Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
            Key.INNSENDING_ID to json[Key.DATA]?.toMap()?.get(Key.INNSENDING_ID),
        ).mapValuesNotNull { it }
            .also {
                logger.info("Publiserte melding med event '${EventName.INNTEKTSMELDING_JOURNALFOERT}' og journalpost-ID '$journalpostId'.")
                sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
            }
    }

    override fun JournalfoerImMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke journalføre.",
                kontekstId = transaksjonId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun JournalfoerImMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@JournalfoerImRiver),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            when (inntektsmelding.type) {
                is Inntektsmelding.Type.Forespurt -> Log.forespoerselId(inntektsmelding.type.id)
                is Inntektsmelding.Type.Selvbestemt -> Log.selvbestemtId(inntektsmelding.type.id)
            },
        )

    private fun opprettOgFerdigstillJournalpost(
        transaksjonId: UUID,
        inntektsmelding: Inntektsmelding,
    ): String {
        "Prøver å opprette og ferdigstille journalpost.".also {
            logger.info(it)
            sikkerLogger.info("$it Gjelder IM:\n$inntektsmelding")
        }

        val response =
            Metrics.dokArkivRequest.recordTime(dokArkivClient::opprettOgFerdigstillJournalpost) {
                dokArkivClient.opprettOgFerdigstillJournalpost(
                    tittel = "Inntektsmelding",
                    gjelderPerson = GjelderPerson(inntektsmelding.sykmeldt.fnr.verdi),
                    avsender =
                        Avsender.Organisasjon(
                            orgnr = inntektsmelding.avsender.orgnr.verdi,
                            navn = inntektsmelding.avsender.orgNavn,
                        ),
                    datoMottatt = LocalDate.now(),
                    dokumenter = tilDokumenter(transaksjonId, inntektsmelding),
                    eksternReferanseId = "ARI-$transaksjonId",
                    callId = "callId_$transaksjonId",
                )
            }

        if (response.journalpostFerdigstilt) {
            "Opprettet og ferdigstilte journalpost med ID '${response.journalpostId}'.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        } else {
            "Opprettet, men ferdigstilte ikke journalpost med ID '${response.journalpostId}'.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }

        return response.journalpostId
    }
}
