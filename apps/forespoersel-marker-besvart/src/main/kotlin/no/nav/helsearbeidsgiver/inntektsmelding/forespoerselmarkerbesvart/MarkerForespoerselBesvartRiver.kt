package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.krev
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.json.toPretty
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.kontrakt.kafkatopic.pri.Pri
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class MarkerBesvartMelding(
    val eventName: EventName,
    val kontekstId: UUID,
    val forespoerselId: UUID,
)

class MarkerForespoerselBesvartRiver(
    private val producer: Producer,
) : ObjectRiver.Simba<MarkerBesvartMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): MarkerBesvartMelding? =
        if (setOf(Key.BEHOV, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            MarkerBesvartMelding(
                eventName = Key.EVENT_NAME.krev(EventName.INNTEKTSMELDING_MOTTATT, EventName.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, data),
            )
        }

    override fun MarkerBesvartMelding.bestemNoekkel(): KafkaKey = KafkaKey(forespoerselId)

    override fun MarkerBesvartMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        logger.info("Mottok melding om ${EventName.INNTEKTSMELDING_MOTTATT}.")
        sikkerLogger.info("Mottok melding:\n${json.toPretty()}.")

        producer
            .send(
                key = forespoerselId,
                message =
                    mapOf(
                        Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART_SIMBA.toJson(Pri.NotisType.serializer()),
                        Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    ),
            )

        "Publiserte melding på pri-topic om ${Pri.NotisType.FORESPOERSEL_BESVART_SIMBA}.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        return null
    }

    override fun MarkerBesvartMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement>? {
        "Klarte ikke publiserte melding på pri-topic om ${Pri.NotisType.FORESPOERSEL_BESVART_SIMBA}.".also {
            logger.error(it)
            sikkerLogger().error(it)
        }

        return null
    }

    override fun MarkerBesvartMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@MarkerForespoerselBesvartRiver),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(forespoerselId),
        )
}
