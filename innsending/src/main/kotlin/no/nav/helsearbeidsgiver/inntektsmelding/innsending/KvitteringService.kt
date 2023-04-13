package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener

// TODO : Duplisert mesteparten av InnsendingService, skal trekke ut i super / generisk løsning
class KvitteringService(val rapidsConnection: RapidsConnection, val redisStore: RedisStore) : River.PacketListener {

    val event: EventName = EventName.KVITTERING_REQUESTED

    init {
        logger.info("Starter kvitteringservice")
        KvitteringStartedListener(this, rapidsConnection)
//        DelegatingFailKanal(EventName.KVITTERING_REQUESTED, this, rapidsConnection)
//        StatefullDataKanal(DataFelter.values().map { it.str }.toTypedArray(), EventName.INSENDING_STARTED, this, rapidsConnection, redisStore)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val transaction: Transaction = startStransactionIfAbsent(packet)

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
                logger.info("Sender event: ${event.name}")
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.BEHOV.str to listOf(BehovType.HENT_PERSISTERT_IM.name),
                            Key.UUID.str to uuid
                        )
                    ).toJson()
                )
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
        logger.info("Finalize kvittering med id=$uuid")
        redisStore.set(uuid, message[Key.INNTEKTSMELDING_DOKUMENT.str].asText())
    }

    fun terminate(message: JsonMessage) {
        val uuid = message[Key.UUID.str].asText()
        logger.info("Terminate kvittering med id=$uuid")
        redisStore.set(message[Key.UUID.str].asText(), message[Key.FAIL.str].asText())
    }

    fun startStransactionIfAbsent(message: JsonMessage): Transaction {
        val uuid = message.get(Key.UUID.str).asText()
        logger.info("Sjekker transaksjon $uuid")
        if (isFailMelding(message)) {
            logger.info("Mottok feilmelding på forespørsel $uuid, avslutter transaksjon")
            return Transaction.TERMINATE
        }
        val eventKey = "${uuid}${event.name}"
        val value = redisStore.get(eventKey)
        if (value.isNullOrEmpty()) {
            redisStore.set(eventKey, uuid)
            return Transaction.NEW
        } else {
            if (isDataCollected(*allData(uuid))) return Transaction.FINALIZE
        }
        return Transaction.IN_PROGRESS
    }

    fun allData(uuid: String) = arrayOf(uuid + DataFelter.INNTEKTSMELDING_DOKUMENT.str)

    fun isDataCollected(vararg keys: String): Boolean = redisStore.exist(*keys) == keys.size.toLong()

    fun isFailMelding(jsonMessage: JsonMessage): Boolean {
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
            }
        }

        override fun onEvent(packet: JsonMessage) {
            mainListener.onPacket(packet, rapidsConnection)
        }
    }
}
