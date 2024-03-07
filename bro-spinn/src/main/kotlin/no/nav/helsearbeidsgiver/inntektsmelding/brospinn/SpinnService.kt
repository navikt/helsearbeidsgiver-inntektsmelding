package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.LagreDataRedisRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

private const val AVSENDER_NAV_NO = "NAV_NO"

class SpinnService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val event = EventName.EKSTERN_INNTEKTSMELDING_REQUESTED
    override val startKeys = setOf(
        Key.FORESPOERSEL_ID,
        Key.SPINN_INNTEKTSMELDING_ID
    )
    override val dataKeys = setOf(
        Key.EKSTERN_INNTEKTSMELDING
    )

    init {
        StatefullEventListener(event, startKeys, rapid, redisStore, ::onPacket)
        LagreDataRedisRiver(event, dataKeys, rapid, redisStore, ::onPacket)
        FailKanal(event, rapid, ::onPacket)
    }

    override fun new(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val spinnImId = Key.SPINN_INNTEKTSMELDING_ID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.transaksjonId(transaksjonId),
            Log.behov(BehovType.HENT_EKSTERN_INNTEKTSMELDING),
            Log.event(event),
            Log.forespoerselId(forespoerselId)
        ) {
            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.HENT_EKSTERN_INNTEKTSMELDING.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.SPINN_INNTEKTSMELDING_ID to spinnImId.toJson(),
                Key.UUID to transaksjonId.toJson()
            )
                .also {
                    logger.info("Publiserte melding om ${BehovType.HENT_EKSTERN_INNTEKTSMELDING.name} for transaksjonId $transaksjonId.")
                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                }
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
        val eksternInntektsmelding = Key.EKSTERN_INNTEKTSMELDING.lesOrNull(EksternInntektsmelding.serializer(), melding)
        if (
            eksternInntektsmelding != null &&
            eksternInntektsmelding.avsenderSystemNavn != AVSENDER_NAV_NO
        ) {
            rapid.publish(
                Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_MOTTATT.toJson(),
                Key.BEHOV to BehovType.LAGRE_EKSTERN_INNTEKTSMELDING.toJson(),
                Key.UUID to randomUuid().toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.EKSTERN_INNTEKTSMELDING to eksternInntektsmelding.toJson(
                    EksternInntektsmelding.serializer()
                )
            ).also {
                MdcUtils.withLogFields(
                    Log.transaksjonId(transaksjonId),
                    Log.behov(BehovType.LAGRE_EKSTERN_INNTEKTSMELDING),
                    Log.event(EventName.EKSTERN_INNTEKTSMELDING_MOTTATT),
                    Log.forespoerselId(forespoerselId)
                ) {
                    logger.info("Publiserte melding om ${BehovType.LAGRE_EKSTERN_INNTEKTSMELDING.name} for transaksjonId $transaksjonId.")
                    sikkerLogger.info("Publiserte melding: ${it.toPretty()}")
                }
            }
        }

        MdcUtils.withLogFields(
            Log.transaksjonId(transaksjonId)
        ) {
            sikkerLogger.info("$event fullført.")
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        MdcUtils.withLogFields(
            Log.transaksjonId(fail.transaksjonId)
        ) {
            sikkerLogger.error("$event terminert.")
        }
    }
}
