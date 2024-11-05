package no.nav.helsearbeidsgiver.inntektsmelding.joark

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.Avsender
import no.nav.helsearbeidsgiver.dokarkiv.domene.GjelderPerson
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toPretty
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.LocalDate
import java.util.UUID

data class JournalfoerImMelding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val inntektsmelding: Inntektsmelding,
    val bestemmendeFravaersdag: LocalDate?,
)

class JournalfoerImRiver(
    private val dokArkivClient: DokArkivClient,
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
                        inntektsmelding = Key.INNTEKTSMELDING.les(Inntektsmelding.serializer(), json),
                        bestemmendeFravaersdag = Key.BESTEMMENDE_FRAVAERSDAG.lesOrNull(LocalDateSerializer, json),
                    )

                EventName.SELVBESTEMT_IM_LAGRET ->
                    JournalfoerImMelding(
                        eventName = eventName,
                        transaksjonId = transaksjonId,
                        inntektsmelding = Key.SELVBESTEMT_INNTEKTSMELDING.les(Inntektsmelding.serializer(), json),
                        bestemmendeFravaersdag = null,
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

        val journalpostId = opprettOgFerdigstillJournalpost(transaksjonId, inntektsmelding, bestemmendeFravaersdag)

        return mapOf(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_JOURNALFOERT.toJson(),
            Key.BEHOV to BehovType.LAGRE_JOURNALPOST_ID.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.JOURNALPOST_ID to journalpostId.toJson(),
            Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
            Key.BESTEMMENDE_FRAVAERSDAG to bestemmendeFravaersdag?.toJson(),
            Key.INNSENDING_ID to json[Key.INNSENDING_ID],
        ).mapValuesNotNull { it }
            .also {
                MdcUtils.withLogFields(
                    Log.behov(BehovType.LAGRE_JOURNALPOST_ID),
                ) {
                    logger.info("Publiserer behov '${BehovType.LAGRE_JOURNALPOST_ID}' med journalpost-ID '$journalpostId'.")
                    sikkerLogger.info("Publiserer behov:\n${it.toPretty()}")
                }
            }
    }

    override fun JournalfoerImMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke journalføre.",
                event = eventName,
                transaksjonId = transaksjonId,
                forespoerselId = null,
                utloesendeMelding =
                    json
                        .plus(
                            Key.BEHOV to BehovType.JOURNALFOER.toJson(),
                        ).toJson(),
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail
            .tilMelding()
            .plus(Key.INNSENDING_ID to json[Key.INNSENDING_ID])
            .mapValuesNotNull { it }
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
        bestemmendeFravaersdag: LocalDate?,
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
                    dokumenter = tilDokumenter(transaksjonId, inntektsmelding, bestemmendeFravaersdag),
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
