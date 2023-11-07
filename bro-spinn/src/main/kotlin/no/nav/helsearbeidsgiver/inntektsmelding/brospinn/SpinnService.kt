package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toJsonMap
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

private const val AVSENDER_NAV_NO = "NAV_NO"

class SpinnService(
    private val rapid: RapidsConnection,
    override val redisStore: IRedisStore
) : CompositeEventListener(redisStore) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val event: EventName = EventName.EKSTERN_INNTEKTSMELDING_REQUESTED

    init {
        withFailKanal { DelegatingFailKanal(event, it, rapid) }
        withDataKanal {
            StatefullDataKanal(
                dataFelter = arrayOf(
                    DataFelt.EKSTERN_INNTEKTSMELDING.str
                ),
                eventName = event,
                mainListener = it,
                rapidsConnection = rapid,
                redisStore = redisStore
            )
        }
        withEventListener { StatefullEventListener(redisStore, event, arrayOf(DataFelt.FORESPOERSEL_ID.str, DataFelt.SPINN_INNTEKTSMELDING_ID.str), it, rapid) }
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val json = message.toJsonMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, json)

        val forespoerselId = RedisKey.of(transaksjonId.toString(), DataFelt.FORESPOERSEL_ID)
            .read()?.let(UUID::fromString)
        if (forespoerselId == null) {
            "Klarte ikke finne forespoerselId for transaksjon $transaksjonId i Redis.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
            return
        }
        val spinnImId = RedisKey.of(transaksjonId.toString(), DataFelt.SPINN_INNTEKTSMELDING_ID)
            .read()?.let(UUID::fromString)
        if (spinnImId == null) {
            "Klarte ikke finne spinnImId for transaksjon $transaksjonId i Redis.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
            return
        }
        MdcUtils.withLogFields(
            Log.transaksjonId(transaksjonId),
            Log.behov(BehovType.HENT_EKSTERN_INNTEKTSMELDING),
            Log.event(event),
            Log.forespoerselId(forespoerselId)
        ) {
            sikkerLogger.info("Prosesserer transaksjon $transaction.")
            if (transaction is Transaction.New) {
                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.HENT_EKSTERN_INNTEKTSMELDING.toJson(),
                    DataFelt.FORESPOERSEL_ID to forespoerselId.toJson(),
                    DataFelt.SPINN_INNTEKTSMELDING_ID to spinnImId.toJson(),
                    Key.UUID to transaksjonId.toJson()
                )
                    .also {
                        logger.info("Publiserte melding om ${BehovType.HENT_EKSTERN_INNTEKTSMELDING.name} for transaksjonId $transaksjonId.")
                        sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                    }
            }
        }
    }

    override fun finalize(message: JsonMessage) {
        val json = message.toJsonMap()
        val transaksjonId = Key.UUID.les(UuidSerializer, json)
        val eksternInntektsmelding = DataFelt.EKSTERN_INNTEKTSMELDING.lesOrNull(EksternInntektsmelding.serializer(), json)
        val forespoerselId = RedisKey.of(transaksjonId.toString(), DataFelt.FORESPOERSEL_ID)
            .read()?.let(UUID::fromString)
        if (
            forespoerselId != null &&
            eksternInntektsmelding?.avsenderSystemNavn != null &&
            eksternInntektsmelding.avsenderSystemNavn != AVSENDER_NAV_NO
        ) {
            rapid.publish(
                Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_MOTTATT.toJson(),
                Key.BEHOV to BehovType.LAGRE_EKSTERN_INNTEKTSMELDING.toJson(),
                Key.UUID to randomUuid().toJson(),
                DataFelt.FORESPOERSEL_ID to forespoerselId.toJson(),
                DataFelt.EKSTERN_INNTEKTSMELDING to eksternInntektsmelding.toJson(
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

    override fun terminate(fail: Fail) {
        MdcUtils.withLogFields(
            Log.transaksjonId(fail.uuid.let(UUID::fromString))
        ) {
            sikkerLogger.error("$event terminert.")
        }
    }

    private fun RedisKey.read(): String? =
        redisStore.get(this)
}
