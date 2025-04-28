package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.json.toPretty
import no.nav.helsearbeidsgiver.felles.kafka.Producer
import no.nav.helsearbeidsgiver.felles.kafka.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
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
) : ObjectRiver<MarkerBesvartMelding>() {
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

        val publisert =
            producer
                .send(
                    key = forespoerselId,
                    message =
                        mapOf(
                            Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART_SIMBA.toJson(Pri.NotisType.serializer()),
                            Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                        ),
                ).getOrThrow()

        logger.info("Publiserte melding på pri-topic om ${Pri.NotisType.FORESPOERSEL_BESVART_SIMBA}.")
        sikkerLogger.info("Publiserte melding på pri-topic:\n${publisert.toPretty()}")

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
