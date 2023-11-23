package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key

class Fail(
    val event: EventName,
    val behov: BehovType? = null,
    val feilmelding: String,
    val uuid: String? = null,
    val jsonMessage: JsonMessage
) {

    init {
        packetValidator.validate(jsonMessage)
        jsonMessage.demandValue(Key.EVENT_NAME.str, event.name)
    }
    companion object {
        val packetValidator = River.PacketValidation {
            it.demandKey(Key.EVENT_NAME.str)
            it.rejectKey(Key.BEHOV.str)
            it.rejectKey(Key.DATA.str)
            it.demandKey(Key.FAIL.str)
            it.interestedIn(Key.UUID.str)
            it.interestedIn(Key.FAILED_BEHOV.str)
            it.interestedIn(Key.FORESPOERSEL_ID.str)
        }

        fun create(event: EventName, behov: BehovType? = null, feilmelding: String, uuid: String? = null, data: Map<IKey, Any> = emptyMap()): Fail {
            return Fail(
                event,
                behov,
                feilmelding,
                uuid,
                jsonMessage = JsonMessage.newMessage(event.name, data.mapKeys { it.key.str }).also {
                    if (behov != null) it[Key.FAILED_BEHOV.str] = behov.name
                    it[Key.FAIL.str] = feilmelding
                }
            )
        }

        fun create(jsonMessage: JsonMessage): Fail {
            val behov = jsonMessage[Key.FAILED_BEHOV.str]
                .takeUnless { it.isMissingOrNull() }
                ?.let {
                    BehovType.valueOf(it.asText())
                }

            val uuid = jsonMessage[Key.UUID.str]
                .takeUnless { it.isMissingOrNull() }
                ?.asText()

            return Fail(
                EventName.valueOf(jsonMessage[Key.EVENT_NAME.str].asText()),
                behov,
                jsonMessage[Key.FAIL.str].asText(),
                uuid,
                jsonMessage
            )
        }
    }
}
