package no.nav.helsearbeidsgiver.felles.test.json

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail

fun JsonElement.readFail(): Fail {
    val json = toString()
    val jsonMessage = JsonMessage(json, MessageProblems(json)).also {
        it.interestedIn(Key.EVENT_NAME, Key.BEHOV, Key.DATA, Key.FAIL)
    }
    return if (!jsonMessage[Key.FAIL.str].isMissingOrNull()) {
        Fail.packetValidator.validate(jsonMessage)
        Fail.create(jsonMessage)
    } else {
        throw RuntimeException("Message må være EVENT, DATA, BEHOV eller FAIL ${jsonMessage.toJson()}")
    }
}
