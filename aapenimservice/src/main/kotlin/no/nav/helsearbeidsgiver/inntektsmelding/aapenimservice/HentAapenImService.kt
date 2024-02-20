package no.nav.helsearbeidsgiver.inntektsmelding.aapenimservice

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

// TODO test
class HentAapenImService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val event = EventName.AAPEN_IM_REQUESTED
    override val startKeys = listOf(
        Key.AAPEN_ID
    )
    override val dataKeys = listOf(
        Key.AAPEN_INNTEKTMELDING
    )

    init {
        StatefullEventListener(rapid, event, redisStore, startKeys, ::onPacket)
        StatefullDataKanal(rapid, event, redisStore, dataKeys, ::onPacket)
        FailKanal(rapid, event, ::onPacket)
    }

    override fun new(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val aapenId = Key.AAPEN_ID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId),
            Log.aapenId(aapenId)
        ) {
            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.HENT_AAPEN_IM.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.AAPEN_ID to aapenId.toJson()
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
        val aapenId = Key.AAPEN_ID.les(UuidSerializer, melding)
        val inntektsmeldingJson = Key.AAPEN_INNTEKTMELDING.les(JsonElement.serializer(), melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId),
            Log.aapenId(aapenId)
        ) {
            val clientId = redisStore.get(RedisKey.of(transaksjonId, event))
                ?.let(UUID::fromString)

            if (clientId == null) {
                sikkerLogger.error("Forsøkte å fullføre, men clientId mangler i Redis.")
            } else {
                val resultJson = ResultJson(success = inntektsmeldingJson).toJsonStr(ResultJson.serializer())
                redisStore.set(RedisKey.of(clientId), resultJson)
            }
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(fail.transaksjonId)
        ) {
            val clientId = redisStore.get(RedisKey.of(fail.transaksjonId, event))
                ?.let(UUID::fromString)

            if (clientId == null) {
                val aapenId = Key.AAPEN_ID.lesOrNull(UuidSerializer, fail.utloesendeMelding.toMap())
                sikkerLogger.error("Forsøkte å terminere, men clientId mangler i Redis. aapenId=$aapenId")
            } else {
                val feilmeldingJson = fail.feilmelding.toJson(String.serializer())
                val resultJson = ResultJson(failure = feilmeldingJson).toJsonStr(ResultJson.serializer())
                redisStore.set(RedisKey.of(clientId), resultJson)
            }
        }
    }
}
