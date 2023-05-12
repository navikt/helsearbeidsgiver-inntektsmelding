package no.nav.helsearbeidsgiver.felles.rapidsrivers.composite

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.toFeilMessage
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.StatefullDataKanal

abstract class CompositeEventListener(open val redisStore: RedisStore) : River.PacketListener {

    abstract val event: EventName
    lateinit var dataKanal: StatefullDataKanal

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val transaction: Transaction = determineTransactionState(packet)

        when (transaction) {
            Transaction.NEW -> {
                initialTransactionState(packet)
                dispatchBehov(packet, transaction)
            }
            Transaction.IN_PROGRESS -> dispatchBehov(packet, transaction)
            Transaction.FINALIZE -> finalize(packet)
            Transaction.TERMINATE -> terminate(packet)
        }
    }

    fun determineTransactionState(message: JsonMessage): Transaction {
        if (isFailMelding(message)) { // Returnerer INPROGRESS eller TERMINATE
            return onError(message.toFeilMessage())
        }
        val uuid = message.get(Key.UUID.str).asText()
        val eventKey = "${uuid}${event.name}"
        val value = redisStore.get(eventKey)
        if (value.isNullOrEmpty()) {
            redisStore.set(eventKey, uuid)
            return Transaction.NEW
        } else {
            if (isDataCollected(uuid)) return Transaction.FINALIZE
        }
        return Transaction.IN_PROGRESS
    }

    fun isFailMelding(jsonMessage: JsonMessage): Boolean {
        try {
            return !(jsonMessage[Key.FAIL.str].isNull || jsonMessage[Key.FAIL.str].isEmpty)
        } catch (e: NoSuchFieldError) {
            return false
        } catch (e: IllegalArgumentException) {
            return false
        }
    }

    abstract fun dispatchBehov(message: JsonMessage, transaction: Transaction)
    abstract fun finalize(message: JsonMessage)
    abstract fun terminate(message: JsonMessage)
    open fun initialTransactionState(message: JsonMessage) {}

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

    open fun isDataCollected(uuid: String): Boolean = dataKanal.isAllDataCollected(uuid)
    open fun isDataCollected(vararg keys: String): Boolean = dataKanal.isDataCollected(*keys)
}
