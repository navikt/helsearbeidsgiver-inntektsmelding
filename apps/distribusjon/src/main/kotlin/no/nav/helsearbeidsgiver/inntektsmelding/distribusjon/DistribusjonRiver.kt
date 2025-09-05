package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.krev
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toPretty
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.JournalfoertInntektsmelding
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class Melding(
    val eventName: EventName,
    val kontekstId: UUID,
    val inntektsmelding: Inntektsmelding,
    val journalpostId: String,
)

class DistribusjonRiver(
    private val producer: Producer,
) : ObjectRiver.Simba<Melding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): Melding? =
        if (setOf(Key.BEHOV, Key.DATA, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            Melding(
                eventName = Key.EVENT_NAME.krev(EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET, EventName.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                inntektsmelding = Key.INNTEKTSMELDING.les(Inntektsmelding.serializer(), json),
                journalpostId = Key.JOURNALPOST_ID.les(String.serializer(), json),
            )
        }

    override fun Melding.bestemNoekkel(): KafkaKey = KafkaKey(inntektsmelding.type.id)

    override fun Melding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        "Forsøker å distribuere IM med journalpost-ID '$journalpostId'.".also {
            logger.info(it)
            sikkerLogger.info("$it Innkommende melding:\n${json.toPretty()}")
        }

        producer
            .send(
                JournalfoertInntektsmelding(
                    journalpostId = journalpostId,
                    inntektsmelding = inntektsmelding,
                ),
            )

        "Distribuerte IM med journalpost-ID '$journalpostId'.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        return mapOf(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_DISTRIBUERT.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.JOURNALPOST_ID to journalpostId.toJson(),
            Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
        )
    }

    override fun Melding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke distribuere IM med journalpost-ID '$journalpostId'.",
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun Melding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@DistribusjonRiver),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
            Log.inntektsmeldingId(inntektsmelding.id),
            when (inntektsmelding.type) {
                is Inntektsmelding.Type.Forespurt,
                is Inntektsmelding.Type.ForespurtEkstern,
                ->
                    Log.forespoerselId(inntektsmelding.type.id)
                is Inntektsmelding.Type.Selvbestemt,
                is Inntektsmelding.Type.Fisker,
                is Inntektsmelding.Type.UtenArbeidsforhold,
                is Inntektsmelding.Type.Behandlingsdager,
                ->
                    Log.selvbestemtId(
                        inntektsmelding.type.id,
                    )
            },
        )
}
