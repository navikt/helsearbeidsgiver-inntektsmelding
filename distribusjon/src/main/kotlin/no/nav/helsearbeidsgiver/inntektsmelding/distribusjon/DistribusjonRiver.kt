package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.JournalfoertInntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toPretty
import no.nav.helsearbeidsgiver.felles.loeser.ObjectRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.fromJson
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
    val journalpostId: String,
    // TODO endre til v1.Inntektsmelding når kun den brukes
    val inntektsmelding: Inntektsmelding,
)

class DistribusjonRiver(
    private val kafkaProducer: KafkaProducer<String, String>,
) : ObjectRiver<Melding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): Melding? {
        val behovType = Key.BEHOV.lesOrNull(BehovType.serializer(), json)
        return if (
            setOf(Key.DATA, Key.FAIL).any(json::containsKey) ||
            (behovType != null && behovType != BehovType.DISTRIBUER_IM)
        ) {
            null
        } else {
            Melding(
                eventName = Key.EVENT_NAME.krev(EventName.INNTEKTSMELDING_JOURNALFOERT, EventName.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                journalpostId = Key.JOURNALPOST_ID.les(String.serializer(), json),
                inntektsmelding = Key.INNTEKTSMELDING_DOKUMENT.les(Inntektsmelding.serializer(), json),
            )
        }
    }

    override fun Melding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        "Forsøker å distribuere IM med journalpost-ID '$journalpostId'.".also {
            logger.info(it)
            sikkerLogger.info("$it Innkommende melding:\n${json.toPretty()}")
        }
        val selvbestemt = json[Key.SELVBESTEMT_ID] != null
        distribuerInntektsmelding(journalpostId, inntektsmelding, selvbestemt)

        "Distribuerte IM med journalpost-ID '$journalpostId'.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        return mapOf(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_DISTRIBUERT.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.JOURNALPOST_ID to journalpostId.toJson(),
            Key.INNTEKTSMELDING_DOKUMENT to inntektsmelding.toJson(Inntektsmelding.serializer()),
            Key.FORESPOERSEL_ID to json[Key.FORESPOERSEL_ID],
            Key.SELVBESTEMT_ID to json[Key.SELVBESTEMT_ID],
        ).mapValuesNotNull { it }
    }

    override fun Melding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke distribuere IM med journalpost-ID: '$journalpostId'.",
                event = eventName,
                transaksjonId = transaksjonId,
                forespoerselId = json[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer),
                utloesendeMelding =
                    json
                        .plus(
                            Key.BEHOV to BehovType.DISTRIBUER_IM.toJson(),
                        ).toJson(),
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail
            .tilMelding()
            .plus(Key.SELVBESTEMT_ID to json[Key.SELVBESTEMT_ID])
            .mapValuesNotNull { it }
    }

    override fun Melding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@DistribusjonRiver),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
        )

    private fun distribuerInntektsmelding(
        journalpostId: String,
        inntektsmelding: Inntektsmelding,
        selvbestemt: Boolean,
    ) {
        val journalfoertInntektsmelding = JournalfoertInntektsmelding(journalpostId, inntektsmelding, selvbestemt)

        val record =
            ProducerRecord<String, String>(
                TOPIC_HELSEARBEIDSGIVER_INNTEKTSMELDING_EKSTERN,
                journalfoertInntektsmelding.toJsonStr(JournalfoertInntektsmelding.serializer()),
            )

        kafkaProducer.send(record)
    }
}
