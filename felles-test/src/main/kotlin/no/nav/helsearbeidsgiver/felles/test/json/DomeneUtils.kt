package no.nav.helsearbeidsgiver.felles.test.json

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Data
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Event
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Message
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import java.lang.RuntimeException

fun <T : Message> JsonNode.toDomeneMessage(validation: River.PacketValidation = River.PacketValidation { }): T {
    val test = this.toJsonElement().toJsonStr(JsonElement.serializer())
    val jsonMessage = JsonMessage(test, MessageProblems(test)).also {
        it.interestedIn(Key.EVENT_NAME, Key.BEHOV, Key.DATA, Key.FAIL)
    }
    validation.validate(jsonMessage)
    if (!jsonMessage[Key.FAIL.str].isMissingOrNull()) {
        Fail.packetValidator.validate(jsonMessage)
        return Fail.create(jsonMessage) as T
    } else if (!jsonMessage[Key.DATA.str].isMissingOrNull()) {
        Data.packetValidator.validate(jsonMessage)
        return Data.create(jsonMessage) as T
    } else if (!jsonMessage[Key.BEHOV.str].isMissingOrNull()) {
        Behov.packetValidator.validate(jsonMessage)
        return Behov.create(jsonMessage) as T
    } else if (!jsonMessage[Key.EVENT_NAME.str].isMissingOrNull()) {
        Event.packetValidator.validate(jsonMessage)
        return Event.create(jsonMessage) as T
    } else {
        throw RuntimeException("Message skall vare enten EVENT, DATA,BEHOV eller Fail ${jsonMessage.toJson()}")
    }
}
