package no.nav.helsearbeidsgiver.felles.rapidsrivers.composite

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault

abstract class CompositeEventListener(open val redisStore: IRedisStore) : River.PacketListener {

    abstract val event: EventName
    private lateinit var dataKanal: StatefullDataKanal

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val transaction: Transaction = determineTransactionState(packet)

        when (transaction) {
            is Transaction.New -> {
                initialTransactionState(packet)
                dispatchBehov(packet, transaction)
            }
            is Transaction.InProgress -> dispatchBehov(packet, transaction)
            is Transaction.Finalize -> finalize(packet)
            is Transaction.Terminate -> terminate(transaction.fail)
            is Transaction.NotActive -> return
        }
    }

    fun determineTransactionState(message: JsonMessage): Transaction {
        // event bør ikke ha UUID men dette er ikke konsistent akkuratt nå så midlertidig blir det sånn til vi får det konsistent.
        // vi trenger også clientID for correlation
        val transactionId = message[Key.UUID.str].asText()
        if (isFailMelding(message)) { // Returnerer INPROGRESS eller TERMINATE
            sikkerLogger().error("Feilmelding er\n${message.toPretty()}")
            val fail = message[Key.FAIL.str].toString().fromJson(Fail.serializer())
            return onError(fail)
        }

        val eventKey = RedisKey.of(transactionId, event)
        val value = redisStore.get(eventKey)

        return when {
            value.isNullOrEmpty() -> {
                if (!isEventMelding(message)) {
                    Transaction.NotActive
                } else {
                    val clientId = message[Key.CLIENT_ID.str]
                        .takeUnless(JsonNode::isMissingOrNull)
                        ?.asText()
                        .orDefault(transactionId)

                    redisStore.set(eventKey, clientId)

                    Transaction.New
                }
            }
            isDataCollected(transactionId) -> Transaction.Finalize
            else -> Transaction.InProgress
        }
    }

    private fun isFailMelding(jsonMessage: JsonMessage): Boolean {
        return try {
            !(jsonMessage[Key.FAIL.str].isNull || jsonMessage[Key.FAIL.str].isEmpty)
        } catch (e: NoSuchFieldError) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    fun isEventMelding(jsonMessage: JsonMessage): Boolean {
        return try {
            !(jsonMessage[Key.EVENT_NAME.str].isMissingOrNull()) &&
                (jsonMessage[Key.DATA.str].isMissingNode && jsonMessage[Key.FAIL.str].isMissingNode && jsonMessage[Key.BEHOV.str].isMissingNode)
        } catch (e: NoSuchFieldError) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    abstract fun dispatchBehov(message: JsonMessage, transaction: Transaction)
    abstract fun finalize(message: JsonMessage)
    abstract fun terminate(fail: Fail)
    open fun initialTransactionState(message: JsonMessage) {}

    open fun onError(feil: Fail): Transaction {
        return Transaction.Terminate(feil)
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

    open fun isDataCollected(uuid: String): Boolean = dataKanal.isAllDataCollected(RedisKey.of(uuid))
    open fun isDataCollected(vararg keys: RedisKey): Boolean = dataKanal.isDataCollected(*keys)
}
