package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.util.UUID

private val sikkerLogger = sikkerLogger()

class Behov(
    val event: EventName,
    val behov: BehovType,
    val forespoerselId: String?,
    val jsonMessage: JsonMessage,
) {
    init {
        packetValidator.validate(jsonMessage)
        jsonMessage.demandValue(Key.EVENT_NAME.str, event.name)
        jsonMessage.demandValue(Key.BEHOV.str, behov.name)
    }

    companion object {
        val packetValidator =
            River.PacketValidation {
                it.demandKey(Key.EVENT_NAME.str)
                it.demandKey(Key.BEHOV.str)
                it.rejectKey(Key.DATA.str)
                it.rejectKey(Key.FAIL.str)
                it.interestedIn(Key.UUID.str)
                it.interestedIn(Key.FORESPOERSEL_ID.str)
                it.interestedIn(Key.SELVBESTEMT_ID.str)
            }
    }

    operator fun get(key: Key): JsonNode = jsonMessage[key.str]

    fun createFail(feilmelding: String): Fail {
        val json = jsonMessage.toJson().parseJson()
        return Fail(
            feilmelding = feilmelding,
            event = event,
            transaksjonId =
                json
                    .toMap()[Key.UUID]
                    ?.fromJson(UuidSerializer)
                    .orDefault {
                        UUID.randomUUID().also {
                            sikkerLogger.error("Mangler transaksjonId i Behov. Erstatter med ny, tilfeldig UUID '$it'.\n${json.toPretty()}")
                        }
                    },
            forespoerselId =
                forespoerselId
                    ?.takeUnless { it.isBlank() }
                    ?.let(UUID::fromString)
                    .also {
                        if (it == null) {
                            sikkerLogger.error("Mangler forespoerselId i Behov.\n${json.toPretty()}")
                        }
                    },
            utloesendeMelding = json,
        )
    }
}
