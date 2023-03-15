package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener

class MoreGenericInnsendingProcessor(val rapidsConnection: RapidsConnection, val redisStore: RedisStore) : River.PacketListener {

    val event: EventName = EventName.INNTEKTSMELDING_REQUESTED

    init {
        InnsendingStartedListener(this, rapidsConnection)
        GenericFailListener(this, rapidsConnection)
        GenericDataPackageListener(DataFelter.values(), this, rapidsConnection, redisStore)
    }

    enum class DataFelter(val str: String) {
        VIRKSOMHET("virksomhet"),
        ARBEIDSFORHOLD("arbeidsforhold"),
        INNTEKTSMELDING_REQUEST("inntektsmelding-request"),
        INNTEKTSMELDING_DOKUMENT("inntektsmelding-dokument");
    }

    class InnsendingStartedListener(val mainListener: River.PacketListener, rapidsConnection: RapidsConnection) : EventListener(rapidsConnection) {

        override val event: EventName = EventName.INSENDING_STARTED

        override fun accept(): River.PacketValidation {
            return River.PacketValidation {
                it.interestedIn(DataFelter.INNTEKTSMELDING_REQUEST.str)
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
                            Key.BEHOV.str to BehovType.VIRKSOMHET.name,
                            Key.ID.str to message[Key.ID.name].asText(),
                            Key.ORGNRUNDERENHET.str to message[Key.ORGNRUNDERENHET.name].asText()
                        )
                    ).toJson()
                )
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.BEHOV.str to BehovType.ARBEIDSFORHOLD.name,
                            Key.ID.str to message[Key.ID.name].asText(),
                            Key.IDENTITETSNUMMER.str to message[Key.IDENTITETSNUMMER.name].asText()
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
                                Key.BEHOV.str to BehovType.PERSISTER_IM.name,
                                Key.INNTEKTSMELDING.str to redisStore.get(uuid + DataFelter.INNTEKTSMELDING_REQUEST)!!
                            )
                        ).toJson()
                    )
                }
            }
            Transaction.FINALIZE -> {
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT.name,
                            Key.INNTEKTSMELDING.str to redisStore.get(uuid + DataFelter.INNTEKTSMELDING_DOKUMENT)!!,
                            Key.UUID.str to uuid
                        )
                    ).toJson()
                )
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
                    Key.INNTEKTSMELDING_DOKUMENT.str to message[Key.INNTEKTSMELDING_DOKUMENT.str],
                    Key.UUID.str to message[Key.UUID.str]
                )
            ).toJson()
        )
    }

    fun startStransactionIfAbsent(message: JsonMessage): Transaction {
        if (!message[Key.FAIL.str].asText().isNullOrEmpty()) {
            return Transaction.TERMINATE
        }
        val uuid = message.get(Key.UUID.str).asText()
        val eventKey = "${uuid}_${event.name}"
        val value = redisStore.get(eventKey)
        if (value.isNullOrEmpty()) {
            redisStore.set(eventKey, uuid)
            val requestKey = "${uuid}_${event.name}"
            redisStore.set(requestKey, message[DataFelter.INNTEKTSMELDING_REQUEST.str].asText())
            return Transaction.NEW
        } else {
            if (isDataCollected(*allData(uuid))) return Transaction.FINALIZE
        }
        return Transaction.IN_PROGRESS
    }

    fun step1data(uuid: String): Array<String> = arrayOf(uuid + DataFelter.VIRKSOMHET, uuid + DataFelter.ARBEIDSFORHOLD)
    fun allData(uuid: String) = step1data(uuid) + (uuid + DataFelter.INNTEKTSMELDING_DOKUMENT)

    fun isDataCollected(vararg keys: String): Boolean = redisStore.exist(*keys) == keys.size.toLong()
}
