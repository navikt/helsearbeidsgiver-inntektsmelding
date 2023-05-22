package no.nav.helsearbeidsgiver.felles.rapidsrivers.composite

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.toFeilMessage
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.StatefullDataKanal
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

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
        // event bør ikke ha UUID men dette er ikke konsistent akkuratt nå så midlertidig blir det sånn til vi får det konsistent.
        // vi trenger også clientID for correlation
        var transactionId = message.get(Key.UUID.str).asText()
        if (isFailMelding(message)) { // Returnerer INPROGRESS eller TERMINATE
            sikkerLogger().info("Feilmelding er ${message.toJson()}")
            return onError(message.toFeilMessage())
        }
        /*
        if (isEventMelding(message)) {
            if (message[Key.UUID.str].isNull || message[Key.UUID.str].isEmpty) {
                transactionId = UUID.randomUUID().toString()
                sikkerLogger().info("Event transaksjonsID er generert $transactionId")
            } else {
                sikkerLogger().info("Event transaksjonsID er ${message[Key.UUID.str].asText()}")
            }
        } else {
            sikkerLogger().info("transaksjonsID er ${message[Key.UUID.str].asText()}")
        }
        */
        val eventKey = "${transactionId}${event.name}"
        val value = redisStore.get(eventKey)
        if (value.isNullOrEmpty()) {
            redisStore.set(eventKey, transactionId)
            return Transaction.NEW
        } else {
            if (isDataCollected(transactionId)) return Transaction.FINALIZE
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

    fun isEventMelding(jsonMessage: JsonMessage): Boolean {
        try {
            return !(jsonMessage[Key.EVENT_NAME.str].isMissingOrNull()) &&
                (jsonMessage[Key.DATA.str].isMissingNode && jsonMessage[Key.FAIL.str].isMissingNode && jsonMessage[Key.BEHOV.str].isMissingNode)
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
