package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektData
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.inntektsmelding.akkumulator.logger
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class InntektService(
    private val rapidsConnection: RapidsConnection,
    override val redisStore: IRedisStore
) : CompositeEventListener(redisStore) {
    override val event: EventName = EventName.INNTEKT_REQUESTED

    init {
        withEventListener {
            StatefullEventListener(
                redisStore,
                event,
                listOf(
                    DataFelt.FORESPOERSEL_ID.str,
                    DataFelt.INNTEKT_DATO.str
                ).toTypedArray(),
                it,
                rapidsConnection
            )
        }
        withDataKanal {
            StatefullDataKanal(
                listOf(
                    DataFelt.FORESPOERSEL_SVAR.str,
                    DataFelt.INNTEKT.str
                ).toTypedArray(),
                event,
                it,
                rapidsConnection,
                redisStore
            )
        }
        withFailKanal { DelegatingFailKanal(event, it, rapidsConnection) }
    }
    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val uuid = message[Key.UUID.str].asText()
        sikkerLogger().info("Dispatcher for $uuid with trans state $transaction")
        println("Dispatcher for $uuid with trans state $transaction")
        if (transaction == Transaction.NEW) {
            sikkerLogger().info("Dispatcher HENT_TRENGER_IM for $uuid")
            sikkerLogger().info("${this.javaClass.simpleName} Dispatcher HENT_TRENGER_IM for $uuid")
            rapidsConnection.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to listOf(BehovType.HENT_TRENGER_IM).toJson(BehovType.serializer().list()),
                Key.UUID to uuid.toJson(),
                Key.BOOMERANG to mapOf(
                    Key.INITIATE_ID.str to uuid.toJson(),
                    Key.INITIATE_EVENT.str to EventName.TRENGER_REQUESTED.toJson(),
                    DataFelt.INNTEKT_DATO.str to redisStore.get(RedisKey.of(uuid, DataFelt.INNTEKT_DATO))!!.toJson()
                ).toJson(),
                DataFelt.FORESPOERSEL_ID to redisStore.get(RedisKey.of(uuid, DataFelt.FORESPOERSEL_ID))!!.toJson()
            )
        } else if (transaction == Transaction.IN_PROGRESS) {
            if (isDataCollected(*step1data(uuid))) {
                val forespurtData: TrengerInntekt = redisStore.get(RedisKey.of(uuid, DataFelt.FORESPOERSEL_SVAR))!!.fromJson(
                    TrengerInntekt.serializer()
                )
                logger.info("${this.javaClass.simpleName} Dispatcher INNTEKT for $uuid")
                rapidsConnection.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to listOf(BehovType.INNTEKT).toJson(BehovType.serializer().list()),
                    Key.UUID to uuid.toJson(),
                    DataFelt.TRENGER_INNTEKT to forespurtData.toJson(TrengerInntekt.serializer())
                )
            }
        } else {
            logger.error("Illegal transaction type ecountered in dispatchBehov $transaction for uuid= $uuid")
        }
    }

    override fun finalize(message: JsonMessage) {
        val uuid = message[Key.UUID.str].asText()
        val feilReport: FeilReport? = redisStore.get(RedisKey.of(uuid = uuid, Feilmelding("")))?.fromJson(
            FeilReport.serializer()
        )
        val clientID = redisStore.get(RedisKey.of(uuid, EventName.INNTEKT_REQUESTED))
        val inntekt = redisStore.get(RedisKey.of(uuid, DataFelt.INNTEKT))?.fromJson(Inntekt.serializer())

        val inntektData = InntektData(inntekt!!.gjennomsnitt(), inntekt!!.historisk, feilReport)

        redisStore.set(RedisKey.of(clientID!!), inntektData.toJsonStr(InntektData.serializer()))
    }

    override fun terminate(message: JsonMessage) {
        TODO("Not yet implemented")
    }

    private fun step1data(uuid: String): Array<RedisKey> = arrayOf(
        RedisKey.of(uuid, DataFelt.FORESPOERSEL_SVAR)
    )
}
