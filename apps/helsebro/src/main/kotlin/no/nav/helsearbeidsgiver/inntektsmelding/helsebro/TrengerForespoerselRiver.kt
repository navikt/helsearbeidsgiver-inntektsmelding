package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.kafka.Producer
import no.nav.helsearbeidsgiver.felles.kafka.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class TrengerForespoerselMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val kontekstId: UUID,
    val data: Map<Key, JsonElement>,
    val forespoerselId: UUID,
)

class TrengerForespoerselRiver(
    private val producer: Producer,
) : ObjectRiver<TrengerForespoerselMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): TrengerForespoerselMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            TrengerForespoerselMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.HENT_TRENGER_IM, BehovType.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                data = data,
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, data),
            )
        }

    override fun TrengerForespoerselMelding.bestemNoekkel(): KafkaKey = KafkaKey(forespoerselId)

    override fun TrengerForespoerselMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        producer
            .send(
                key = forespoerselId,
                message =
                    mapOf(
                        Pri.Key.BEHOV to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(Pri.BehovType.serializer()),
                        Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                        Pri.Key.BOOMERANG to
                            mapOf(
                                Key.EVENT_NAME to eventName.toJson(),
                                Key.KONTEKST_ID to kontekstId.toJson(),
                                Key.DATA to data.toJson(),
                            ).toJson(),
                    ),
            )

        "Publiserte melding på pri-topic om ${Pri.BehovType.TRENGER_FORESPØRSEL}.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        return null
    }

    override fun TrengerForespoerselMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke spørre Storebror om forespørsel.",
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun TrengerForespoerselMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@TrengerForespoerselRiver),
            Log.event(eventName),
            Log.behov(BehovType.HENT_TRENGER_IM),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(forespoerselId),
        )
}
