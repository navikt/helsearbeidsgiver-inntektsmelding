package no.nav.helsearbeidsgiver.felles.rapidsrivers.composite

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.MessageListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Event
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Message
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

abstract class CompositeEventListener(open val redisStore: IRedisStore) : MessageListener {

    abstract val event: EventName
    private lateinit var dataKanal: StatefullDataKanal

    override fun onMessage(message: Message) {
        val transaction: Transaction = determineTransactionState(message as TxMessage)

        when (transaction) {
            Transaction.NEW -> {
                initialTransactionState(message)
                dispatchBehov(message, transaction)
            }
            Transaction.IN_PROGRESS -> dispatchBehov(message, transaction)
            Transaction.FINALIZE -> finalize(message)
            Transaction.TERMINATE -> terminate(message)
            Transaction.NOT_ACTIVE -> return
        }
    }

    fun determineTransactionState(message: TxMessage): Transaction {
        // event bør ikke ha UUID men dette er ikke konsistent akkuratt nå så midlertidig blir det sånn til vi får det konsistent.
        // vi trenger også clientID for correlation
        val transactionId = message.uuid()
        if (message is no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail) { // Returnerer INPROGRESS eller TERMINATE
            sikkerLogger().error("Feilmelding er ${message.toJsonMessage().toJson()}")
            return onError(message)
        }

        val eventKey = RedisKey.of(transactionId, event)
        val value = redisStore.get(eventKey)
        if (value.isNullOrEmpty()) {
            if (message !is Event) return Transaction.NOT_ACTIVE

            val clientId = if (message.clientId.isNullOrBlank()) transactionId else message.clientId
            redisStore.set(eventKey, clientId)
            return Transaction.NEW
        } else {
            if (isDataCollected(transactionId)) return Transaction.FINALIZE
        }
        return Transaction.IN_PROGRESS
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

    abstract fun dispatchBehov(message: Message, transaction: Transaction)
    abstract fun finalize(message: Message)
    abstract fun terminate(message: Message)
    open fun initialTransactionState(message: Message) {}

    open fun onError(feil: no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail): Transaction {
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

    open fun isDataCollected(uuid: String): Boolean = dataKanal.isAllDataCollected(RedisKey.of(uuid))
    open fun isDataCollected(vararg keys: RedisKey): Boolean = dataKanal.isDataCollected(*keys)
}
