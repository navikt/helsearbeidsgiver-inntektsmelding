package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.JournalfoertInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toPretty
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID

const val TOPIC_HELSEARBEIDSGIVER_INNTEKTSMELDING_EKSTERN = "helsearbeidsgiver.inntektsmelding"

data class Melding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val inntektsmelding: Inntektsmelding,
    val journalpostId: String,
)

class DistribusjonRiver(
    private val kafkaProducer: KafkaProducer<String, String>,
) : ObjectRiver<Melding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): Melding? =
        if (setOf(Key.BEHOV, Key.DATA, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            Melding(
                eventName = Key.EVENT_NAME.krev(EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET, EventName.serializer(), json),
                transaksjonId = Key.KONTEKST_ID.les(UuidSerializer, json),
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

        distribuerInntektsmelding(journalpostId, inntektsmelding)

        "Distribuerte IM med journalpost-ID '$journalpostId'.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        return mapOf(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_DISTRIBUERT.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(),
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
                kontekstId = transaksjonId,
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
            Log.transaksjonId(transaksjonId),
            when (inntektsmelding.type) {
                is Inntektsmelding.Type.Forespurt -> Log.forespoerselId(inntektsmelding.type.id)
                is Inntektsmelding.Type.Selvbestemt -> Log.selvbestemtId(inntektsmelding.type.id)
            },
        )

    private fun distribuerInntektsmelding(
        journalpostId: String,
        inntektsmelding: Inntektsmelding,
    ) {
        val journalfoertInntektsmelding =
            JournalfoertInntektsmelding(
                journalpostId = journalpostId,
                inntektsmelding = inntektsmelding,
            )

        val record =
            ProducerRecord<String, String>(
                TOPIC_HELSEARBEIDSGIVER_INNTEKTSMELDING_EKSTERN,
                journalfoertInntektsmelding.toJsonStr(JournalfoertInntektsmelding.serializer()),
            )

        kafkaProducer.send(record)
    }
}
