package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener

// TODO : Duplisert mesteparten av InnsendingService, skal trekke ut i super / generisk løsning.
class KvitteringService(val rapidsConnection: RapidsConnection, val redisStore: RedisStore) : River.PacketListener {

    val event: EventName = EventName.KVITTERING_REQUESTED
    val listener: KvitteringStartedListener
    init {
        logger.info("Starter kvitteringservice")
        listener = KvitteringStartedListener(this, rapidsConnection)
        DelegatingFailKanal(EventName.KVITTERING_REQUESTED, this, rapidsConnection)
        StatefullDataKanal(DataFelter.values().map { it.str }.toTypedArray(), EventName.KVITTERING_REQUESTED, this, rapidsConnection, redisStore)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val transaction: Transaction = startTransactionIfAbsent(packet)

        when (transaction) {
            Transaction.NEW -> dispatchBehov(packet, transaction)
            Transaction.IN_PROGRESS -> dispatchBehov(packet, transaction)
            Transaction.FINALIZE -> finalize(packet)
            Transaction.TERMINATE -> terminate(packet)
        }
    }

    private fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        when (transaction) {
            Transaction.NEW -> {
                val uuid: String = message[Key.UUID.str].asText()
                val transactionId: String = message[Key.INITIATE_ID.str].asText()
                logger.info("Sender event: ${event.name} for forespørsel $uuid")
                val msg = JsonMessage.newMessage(
                    mapOf(
                        Key.BEHOV.str to listOf(BehovType.HENT_PERSISTERT_IM.name),
                        Key.EVENT_NAME.str to event.name,
                        Key.UUID.str to uuid,
                        Key.INITIATE_ID.str to transactionId
                    )
                ).toJson()
                logger.info("Publiserer melding: $msg")
                rapidsConnection.publish(msg)
            }
            Transaction.IN_PROGRESS -> {
                logger.error("Mottok ${Transaction.IN_PROGRESS}, skal ikke skje")
            }
            Transaction.FINALIZE -> {
                logger.error("Mottok ${Transaction.FINALIZE}, skal ikke skje")
            }
            Transaction.TERMINATE -> {
                logger.error("Mottok ${Transaction.TERMINATE}, skal ikke skje")
            }
        }
    }

    fun finalize(message: JsonMessage) {
        val uuid = message[Key.UUID.str].asText()
        val transactionId = message[Key.INITIATE_ID.str].asText()
        val dok = message[Key.INNTEKTSMELDING_DOKUMENT.str].asText()
        logger.info("Finalize kvittering med id=$uuid")
        redisStore.set(transactionId, dok)
    }

    fun terminate(message: JsonMessage) {
        val uuid = message[Key.UUID.str].asText()
        logger.info("Terminate kvittering med id=$uuid")
        redisStore.set(message[Key.INITIATE_ID.str].asText(), message[Key.FAIL.str].asText())
    }

    private fun startTransactionIfAbsent(message: JsonMessage): Transaction {
        sikkerlogg.info("Mottok melding ${message.toJson()}")
        val uuid = message[Key.UUID.str].asText()
        val transactionId = message[Key.INITIATE_ID.str].asText()
        logger.info("Sjekker transaksjon $transactionId for forespørsel: $uuid")
        if (feilmelding(message)) {
            logger.info("Mottok feilmelding på forespørsel $uuid, avslutter transaksjon")
            return Transaction.TERMINATE
        }
        val eventKey = "$uuid-$transactionId"
        // ^ bruke event.name + transactionId for mer generisk løsning hvis flere samtidige behov
        val value = redisStore.get(eventKey)
        return if (value.isNullOrEmpty()) {
            redisStore.set(eventKey, uuid)
            Transaction.NEW
        } else {
            Transaction.FINALIZE // Vi venter kun på en melding, ellers må man sjekke at man har fått alt i redis
        }
    }

    private fun feilmelding(jsonMessage: JsonMessage): Boolean {
        try {
            return !jsonMessage[Key.FAIL.str].asText().isNullOrEmpty()
        } catch (e: NoSuchFieldError) {
            return false
        } catch (e: IllegalArgumentException) {
            return false
        }
    }

    class KvitteringStartedListener(val mainListener: River.PacketListener, rapidsConnection: RapidsConnection) : EventListener(rapidsConnection) {

        override val event: EventName = EventName.KVITTERING_REQUESTED

        override fun accept(): River.PacketValidation {
            return River.PacketValidation {
                it.requireKey(Key.INITIATE_ID.str, Key.UUID.str)
            }
        }

        override fun onEvent(packet: JsonMessage) {
            mainListener.onPacket(packet, rapidsConnection)
        }
    }
}
