package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.InnsendtInntektsmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.LagreDataRedisRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.LagreStartDataRedisRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import java.util.UUID

class KvitteringService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore,
) : CompositeEventListener() {
    private val logger = logger()

    override val event = EventName.KVITTERING_REQUESTED
    override val startKeys =
        setOf(
            Key.FORESPOERSEL_ID,
        )
    override val dataKeys =
        setOf(
            Key.INNTEKTSMELDING_DOKUMENT,
            Key.EKSTERN_INNTEKTSMELDING,
        )

    init {
        LagreStartDataRedisRiver(event, startKeys, rapid, redisStore, ::onPacket)
        LagreDataRedisRiver(event, dataKeys, rapid, redisStore, ::onPacket)
        FailKanal(event, rapid, ::onPacket)
    }

    override fun new(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(String.serializer(), melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(String.serializer(), melding)

        logger.info("Sender event: ${event.name} for forespørsel $forespoerselId")

        rapid
            .publish(
                Key.BEHOV to BehovType.HENT_PERSISTERT_IM.toJson(),
                Key.EVENT_NAME to event.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            ).also {
                logger.info("Publiserte melding: ${it.toPretty()}")
            }
    }

    override fun inProgress(melding: Map<Key, JsonElement>) {
        "Service skal aldri være \"underveis\".".also {
            logger.error(it)
            sikkerLogger.error(it)
        }
    }

    override fun finalize(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val clientId = redisStore.get(RedisKey.of(transaksjonId, event))!!.let(UUID::fromString)

        // TODO: Skriv bort fra empty payload hvis mulig
        val resultJson =
            ResultJson(
                success =
                    InnsendtInntektsmelding(
                        Key.INNTEKTSMELDING_DOKUMENT
                            .les(String.serializer(), melding)
                            .takeIf { it != "{}" }
                            ?.fromJson(Inntektsmelding.serializer()),
                        Key.EKSTERN_INNTEKTSMELDING
                            .les(String.serializer(), melding)
                            .takeIf { it != "{}" }
                            ?.fromJson(EksternInntektsmelding.serializer()),
                    ).toJson(InnsendtInntektsmelding.serializer()),
            )

        logger.info("Finalize kvittering med transaksjonId=$transaksjonId")

        redisStore.set(RedisKey.of(clientId), resultJson.toJsonStr())
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        val clientId =
            redisStore
                .get(RedisKey.of(fail.transaksjonId, event))
                ?.let(UUID::fromString)

        if (clientId == null) {
            MdcUtils.withLogFields(
                Log.transaksjonId(fail.transaksjonId),
            ) {
                sikkerLogger.error("Forsøkte å terminere, men clientId mangler i Redis. forespoerselId=${fail.forespoerselId}")
            }
        } else {
            logger.info("Terminate kvittering med forespoerselId=${fail.forespoerselId} og transaksjonId ${fail.transaksjonId}")
            val resultJson =
                ResultJson(
                    failure = fail.feilmelding.toJson(),
                )
            redisStore.set(RedisKey.of(clientId), resultJson.toJsonStr())
        }
    }
}
