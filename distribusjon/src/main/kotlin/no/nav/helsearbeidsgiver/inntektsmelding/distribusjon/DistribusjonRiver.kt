package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Utils.convert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toPretty
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.LocalDate
import java.util.UUID
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding as InntektsmeldingGammel

const val TOPIC_HELSEARBEIDSGIVER_INNTEKTSMELDING_EKSTERN = "helsearbeidsgiver.inntektsmelding"

data class Melding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val inntektsmelding: Inntektsmelding,
    val bestemmendeFravaersdag: LocalDate?,
    val journalpostId: String,
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
                inntektsmelding = Key.INNTEKTSMELDING.les(Inntektsmelding.serializer(), json),
                bestemmendeFravaersdag = Key.BESTEMMENDE_FRAVAERSDAG.lesOrNull(LocalDateSerializer, json),
                journalpostId = Key.JOURNALPOST_ID.les(String.serializer(), json),
            )
        }
    }

    override fun Melding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        "Forsøker å distribuere IM med journalpost-ID '$journalpostId'.".also {
            logger.info(it)
            sikkerLogger.info("$it Innkommende melding:\n${json.toPretty()}")
        }

        distribuerInntektsmelding(inntektsmelding, bestemmendeFravaersdag, journalpostId)

        "Distribuerte IM med journalpost-ID '$journalpostId'.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        return mapOf(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_DISTRIBUERT.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.JOURNALPOST_ID to journalpostId.toJson(),
            Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
            Key.BESTEMMENDE_FRAVAERSDAG to bestemmendeFravaersdag?.toJson(),
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
                forespoerselId = null,
                utloesendeMelding =
                    json
                        .plus(
                            Key.BEHOV to BehovType.DISTRIBUER_IM.toJson(),
                        ).toJson(),
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
        inntektsmelding: Inntektsmelding,
        bestemmendeFravaersdag: LocalDate?,
        journalpostId: String,
    ) {
        val inntektsmeldingGammeltFormat =
            inntektsmelding
                .convert()
                .let {
                    if (bestemmendeFravaersdag != null) {
                        it.copy(bestemmendeFraværsdag = bestemmendeFravaersdag)
                    } else {
                        it
                    }
                }

        val erSelvbestemt = inntektsmelding.type is Inntektsmelding.Type.Selvbestemt

        val journalfoertInntektsmelding =
            JournalfoertInntektsmelding(
                journalpostId = journalpostId,
                inntektsmeldingV1 = inntektsmelding,
                bestemmendeFravaersdag = bestemmendeFravaersdag,
                inntektsmelding = inntektsmeldingGammeltFormat,
                selvbestemt = erSelvbestemt,
            )

        val record =
            ProducerRecord<String, String>(
                TOPIC_HELSEARBEIDSGIVER_INNTEKTSMELDING_EKSTERN,
                journalfoertInntektsmelding.toJsonStr(JournalfoertInntektsmelding.serializer()),
            )

        kafkaProducer.send(record)
    }
}

// Midlertidig klasse som inneholder både gammelt og nytt format
@Serializable
data class JournalfoertInntektsmelding(
    val journalpostId: String,
    val inntektsmeldingV1: Inntektsmelding,
    @Serializable(LocalDateSerializer::class)
    val bestemmendeFravaersdag: LocalDate?,
    val inntektsmelding: InntektsmeldingGammel,
    val selvbestemt: Boolean, // for å skille på selvbestemt og vanlig i spinosaurus, før V1 tas i bruk overalt
)
