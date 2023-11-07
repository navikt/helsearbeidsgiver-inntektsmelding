package no.nav.helsearbeidsgiver.felles.test.json

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Data
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Event
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Message
import java.lang.RuntimeException

fun <T : Message> JsonElement.toDomeneMessage(validation: River.PacketValidation = River.PacketValidation { }): T {
    val json = toString()
    val jsonMessage = JsonMessage(json, MessageProblems(json)).also {
        it.interestedIn(Key.EVENT_NAME, Key.BEHOV, Key.DATA, Key.FAIL)
    }
    validation.validate(jsonMessage)
    return if (!jsonMessage[Key.FAIL.str].isMissingOrNull()) {
        Fail.packetValidator.validate(jsonMessage)
        Fail.create(jsonMessage) as T
    } else if (!jsonMessage[Key.DATA.str].isMissingOrNull()) {
        Data.packetValidator.validate(jsonMessage)
        Data.create(jsonMessage) as T
    } else if (!jsonMessage[Key.BEHOV.str].isMissingOrNull()) {
        Behov.packetValidator.validate(jsonMessage)
        Behov.create(jsonMessage) as T
    } else if (!jsonMessage[Key.EVENT_NAME.str].isMissingOrNull()) {
        Event.packetValidator.validate(jsonMessage)
        Event.create(jsonMessage) as T
    } else {
        throw RuntimeException("Message må være EVENT, DATA, BEHOV eller FAIL ${jsonMessage.toJson()}")
    }
}
