package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
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
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import java.util.UUID

class InnsendingService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val logger = logger()

    override val event = EventName.INSENDING_STARTED
    override val startKeys = setOf(
        Key.FORESPOERSEL_ID,
        Key.ORGNRUNDERENHET,
        Key.IDENTITETSNUMMER,
        Key.ARBEIDSGIVER_ID,
        Key.SKJEMA_INNTEKTSMELDING
    )
    override val dataKeys = setOf(
        Key.ER_DUPLIKAT_IM,
        Key.PERSISTERT_SKJEMA_INNTEKTSMELDING
    )

    init {
        LagreStartDataRedisRiver(event, startKeys, rapid, redisStore, ::onPacket)
        LagreDataRedisRiver(event, dataKeys, rapid, redisStore, ::onPacket)
        FailKanal(event, rapid, ::onPacket)
    }

    override fun new(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val skjema = Key.SKJEMA_INNTEKTSMELDING.les(Innsending.serializer(), melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId)
        ) {
            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.PERSISTER_IM_SKJEMA.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(Innsending.serializer())
            )
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
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val erDuplikat = Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), melding)
        val skjema = Key.SKJEMA_INNTEKTSMELDING.les(Innsending.serializer(), melding)
        val orgnr = Key.ORGNRUNDERENHET.les(String.serializer(), melding)
        val sykmeldtFnr = Key.IDENTITETSNUMMER.les(String.serializer(), melding)
        val innsenderFnr = Key.ARBEIDSGIVER_ID.les(String.serializer(), melding)

        val clientId = redisStore.get(RedisKey.of(transaksjonId, event))!!.let(UUID::fromString)

        logger.info("publiserer under clientID $clientId")

        val resultJson = ResultJson(success = skjema.toJson(Innsending.serializer()))
        redisStore.set(RedisKey.of(clientId), resultJson.toJsonStr())

        if (!erDuplikat) {
            logger.info("Publiserer INNTEKTSMELDING_SKJEMA_MOTTATT under uuid $transaksjonId")
            logger.info("InnsendingService: emitting event INNTEKTSMELDING_SKJEMA_MOTTATT")
            rapid.publish(
                Key.EVENT_NAME to EventName.INNTEKTSMELDING_SKJEMA_LAGRET.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.DATA to "".toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.ORGNRUNDERENHET to orgnr.toJson(),
                Key.IDENTITETSNUMMER to sykmeldtFnr.toJson(),
                Key.ARBEIDSGIVER_ID to innsenderFnr.toJson(),
                Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(Innsending.serializer())
            )
                .also {
                    logger.info("Submitting INNTEKTSMELDING_SKJEMA_MOTTATT")
                    sikkerLogger.info("Submitting INNTEKTSMELDING_SKJEMA_MOTTATT ${it.toPretty()}")
                }
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        val clientId = redisStore.get(RedisKey.of(fail.transaksjonId, event))
            ?.let(UUID::fromString)

        if (clientId == null) {
            MdcUtils.withLogFields(
                Log.transaksjonId(fail.transaksjonId)
            ) {
                sikkerLogger.error("Forsøkte å terminere, men clientId mangler i Redis. forespoerselId=${fail.forespoerselId}")
            }
        } else {
            val resultJson = ResultJson(failure = fail.feilmelding.toJson())
            redisStore.set(RedisKey.of(clientId), resultJson.toJsonStr())
        }
    }
}
