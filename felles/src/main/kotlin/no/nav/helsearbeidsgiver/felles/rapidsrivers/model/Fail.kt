package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.TxMessage

class Fail(val event: EventName,
           val behov: BehovType? = null,
           val feilmelding: String,
           val uuid: String? = null,
           private val jsonMessage: JsonMessage) : Message, TxMessage {

    init {
        packetValidator.validate(jsonMessage)
        jsonMessage.demandValue(Key.EVENT_NAME.str,event.name)
    }
    companion object {
        val packetValidator = River.PacketValidation {
            it.demandKey(Key.EVENT_NAME.str)
            it.rejectKey(Key.BEHOV.str)
            it.rejectKey(Key.DATA.str)
            it.demandKey(Key.FAIL.str)
            it.interestedIn(Key.UUID.str)
            it.interestedIn(Key.FAILED_BEHOV.str)
        }

        fun create(event: EventName, behov: BehovType? = null,feilmelding:String, data: Map<IKey, Any> = emptyMap() ) : Fail {
            return Fail(event, behov, feilmelding,
                jsonMessage =  JsonMessage.newMessage(event.name, data.mapKeys { it.key.str }).also {
                    if (behov!=null) it[Key.FAILED_BEHOV.str] = behov.name
                    it[Key.FAIL.str] = feilmelding
                })
        }

        fun create(jsonMessage: JsonMessage) : Fail {
            val behov = jsonMessage[Key.FAILED_BEHOV.str]
                        .takeUnless { it.isMissingOrNull()}
                        ?.let {
                            BehovType.valueOf(it.asText())
                        }

            val uuid  = jsonMessage[Key.UUID.str]
                        .takeUnless { it.isMissingOrNull() }
                        ?.let {
                            it.asText()
                        }


            return Fail(EventName.valueOf(jsonMessage[Key.EVENT_NAME.str].asText()), behov, jsonMessage[Key.FAIL.str].asText(), jsonMessage[Key.UUID.str].asText(), jsonMessage)
        }
    }

    override operator fun get(key: IKey): JsonNode =  jsonMessage[key.str]

    override operator fun set(key: IKey, value: Any) { jsonMessage[key.str] = value }

    override fun uuid(): String {
        return uuid!!
    }

    override fun toJsonMessage(): JsonMessage {

        return jsonMessage
    }
}