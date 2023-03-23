package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener

class InnsendingService(val rapidsConnection: RapidsConnection, val redisStore: RedisStore) : River.PacketListener {

    val event: EventName = EventName.INSENDING_STARTED

    init {
        InnsendingStartedListener(this, rapidsConnection)
        DelegatingFailKanal(EventName.INSENDING_STARTED, this, rapidsConnection)
        StatefullDataKanal(DataFelter.values().map { it.str }.toTypedArray(), EventName.INSENDING_STARTED, this, rapidsConnection, redisStore)
    }

    class InnsendingStartedListener(val mainListener: River.PacketListener, rapidsConnection: RapidsConnection) : EventListener(rapidsConnection) {

        override val event: EventName = EventName.INSENDING_STARTED

        override fun accept(): River.PacketValidation {
            return River.PacketValidation {
                it.interestedIn(Key.INNTEKTSMELDING.str)
                it.requireKey(Key.ORGNRUNDERENHET.str)
                it.requireKey(Key.IDENTITETSNUMMER.str)
            }
        }

        override fun onEvent(packet: JsonMessage) {
            mainListener.onPacket(packet, rapidsConnection)
        }
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

    fun terminate(message: JsonMessage) {
        redisStore.set(message[Key.UUID.str].asText(), message[Key.FAIL.str].asText())
    }

    fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val uuid: String = message[Key.UUID.str].asText()
        when (transaction) {
            Transaction.NEW -> {
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.BEHOV.str to listOf(BehovType.VIRKSOMHET.name),
                            Key.ORGNRUNDERENHET.str to message[Key.ORGNRUNDERENHET.str].asText(),
                            Key.UUID.str to uuid
                        )
                    ).toJson()
                )
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.BEHOV.str to listOf(BehovType.ARBEIDSFORHOLD.name),
                            Key.IDENTITETSNUMMER.str to message[Key.IDENTITETSNUMMER.str].asText(),
                            Key.UUID.str to uuid
                        )
                    ).toJson()
                )
            }
            Transaction.IN_PROGRESS -> {
                if (isDataCollected(*step1data(message[Key.UUID.str].asText()))) {
                    rapidsConnection.publish(
                        JsonMessage.newMessage(
                            mapOf(
                                Key.EVENT_NAME.str to event.name,
                                Key.BEHOV.str to listOf(BehovType.PERSISTER_IM.name),
                                Key.INNTEKTSMELDING.str to customObjectMapper().readTree(redisStore.get(uuid + DataFelter.INNTEKTSMELDING_REQUEST.str)!!),
                                Key.UUID.str to uuid
                            )
                        ).toJson()
                    )
                }
            }
            Transaction.FINALIZE -> {
                println("I was not supposed to be hereeeeeeeeeeeeeeeeeeeeeee")
            }
            Transaction.TERMINATE -> {}
        }
    }

    fun finalize(message: JsonMessage) {
        redisStore.set(message[Key.UUID.str].asText(), message[Key.INNTEKTSMELDING_DOKUMENT.str].asText())
        rapidsConnection.publish(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT,
                    Key.INNTEKTSMELDING_DOKUMENT.str to message[Key.INNTEKTSMELDING_DOKUMENT.str].asText(),
                    Key.UUID.str to message[Key.UUID.str]
                )
            ).toJson()
        )
    }

    fun startStransactionIfAbsent(message: JsonMessage): Transaction {
        if (isFailMelding(message)) {
            return Transaction.TERMINATE
        }
        val uuid = message.get(Key.UUID.str).asText()
        val eventKey = "${uuid}${event.name}"
        val value = redisStore.get(eventKey)
        if (value.isNullOrEmpty()) {
            redisStore.set(eventKey, uuid)
            val uuid = redisStore.get(eventKey)
            val requestKey = "${uuid}${DataFelter.INNTEKTSMELDING_REQUEST.str}"
            redisStore.set(requestKey, message[DataFelter.INNTEKTSMELDING_REQUEST.str].toString())
            return Transaction.NEW
        } else {
            if (isDataCollected(*allData(uuid))) return Transaction.FINALIZE
        }
        return Transaction.IN_PROGRESS
    }

    fun isFailMelding(jsonMessage: JsonMessage): Boolean {
        try {
            return !jsonMessage[Key.FAIL.str].asText().isNullOrEmpty()
        } catch (e: NoSuchFieldError) {
            return false
        } catch (e: IllegalArgumentException) {
            return false
        }
    }

    fun step1data(uuid: String): Array<String> = arrayOf(uuid + DataFelter.VIRKSOMHET.str, uuid + DataFelter.ARBEIDSFORHOLD.str)
    fun allData(uuid: String) = step1data(uuid) + (uuid + DataFelter.INNTEKTSMELDING_DOKUMENT.str)

    fun isDataCollected(vararg keys: String): Boolean = redisStore.exist(*keys) == keys.size.toLong()
}
