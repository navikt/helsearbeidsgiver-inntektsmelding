package no.nav.helsearbeidsgiver.felles.rapidsrivers.composite

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

abstract class CompositeEventListener(open val redisStore: RedisStore) : River.PacketListener {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    abstract val event: EventName
    private lateinit var dataKanal: StatefullDataKanal

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val json = packet.toJson().parseJson().toMap()

        if (Key.FORESPOERSEL_ID.lesOrNull(String.serializer(), json).isNullOrEmpty()) {
            logger.warn("Mangler forespørselId!")
            sikkerLogger.warn("Mangler forespørselId!")
        }

        val transaction = determineTransactionState(json)
        when (transaction) {
            Transaction.NEW,
            Transaction.IN_PROGRESS -> dispatchBehov(packet, transaction)
            Transaction.FINALIZE -> finalize(packet)
            Transaction.TERMINATE -> terminate(json[Key.FAIL]!!.fromJson(Fail.serializer()))
            Transaction.NOT_ACTIVE -> return
        }
    }

    fun determineTransactionState(json: Map<IKey, JsonElement>): Transaction {
        val transaksjonId = json[Key.UUID]?.fromJson(UuidSerializer)

        if (transaksjonId == null) {
            "Melding mangler transaksjon-ID. Ignorerer melding.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
            return Transaction.NOT_ACTIVE
        }

        MdcUtils.withLogFields(
            Log.transaksjonId(transaksjonId)
        ) {
            val fail = toFailOrNull(json)
            if (fail != null) {
                sikkerLogger.error("Feilmelding er '${fail.feilmelding}'. Utløsende melding er \n${fail.utloesendeMelding.toPretty()}")
                return onError(fail)
            }

            val clientIdRedisKey = RedisKey.of(transaksjonId, event)
            val lagretClientId = redisStore.get(clientIdRedisKey)

            return when {
                lagretClientId.isNullOrEmpty() -> {
                    if (!isEventMelding(json)) {
                        "Servicen er inaktiv for gitt event. Mest sannsynlig skyldes dette timeout av Redis-verdier.".also {
                            logger.error(it)
                            sikkerLogger.error(it)
                        }
                        Transaction.NOT_ACTIVE
                    } else {
                        val clientId = json[Key.CLIENT_ID]?.fromJson(UuidSerializer)
                            .let { clientId ->
                                if (clientId != null) {
                                    clientId
                                } else {
                                    "Client-ID mangler. Bruker transaksjon-ID som backup.".also {
                                        logger.error(it)
                                        sikkerLogger.error(it)
                                    }
                                    transaksjonId
                                }
                            }

                        redisStore.set(clientIdRedisKey, clientId.toString())

                        Transaction.NEW
                    }
                }

                isDataCollected(transaksjonId) -> Transaction.FINALIZE
                else -> Transaction.IN_PROGRESS
            }
        }
    }

    fun toFailOrNull(json: Map<IKey, JsonElement>): Fail? =
        json[Key.FAIL]
            ?.runCatching {
                fromJson(Fail.serializer())
            }
            ?.getOrNull()

    private fun isEventMelding(json: Map<IKey, JsonElement>): Boolean =
        json[Key.EVENT_NAME] != null &&
            json.keys.intersect(setOf(Key.BEHOV, Key.DATA, Key.FAIL)).isEmpty()

    abstract fun dispatchBehov(message: JsonMessage, transaction: Transaction)
    abstract fun finalize(message: JsonMessage)
    abstract fun terminate(fail: Fail)

    open fun onError(feil: Fail): Transaction {
        return Transaction.TERMINATE
    }

    fun withFailKanal(failKanalSupplier: (t: CompositeEventListener) -> FailKanal): CompositeEventListener {
        failKanalSupplier.invoke(this)
        return this
    }

    fun withEventListener(eventListenerSupplier: (t: CompositeEventListener) -> EventListener): CompositeEventListener {
        eventListenerSupplier.invoke(this)
        return this
    }

    fun withDataKanal(dataKanalSupplier: (t: CompositeEventListener) -> StatefullDataKanal): CompositeEventListener {
        dataKanal = dataKanalSupplier.invoke(this)
        return this
    }

    open fun isDataCollected(uuid: UUID): Boolean = dataKanal.isAllDataCollected(uuid)
    open fun isDataCollected(vararg keys: RedisKey): Boolean = dataKanal.isDataCollected(*keys)
}
