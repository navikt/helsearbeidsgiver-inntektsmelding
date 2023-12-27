package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.utils.mapOfNotNull
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
    val jsonMessage: JsonMessage
) {

    init {
        packetValidator.validate(jsonMessage)
        jsonMessage.demandValue(Key.EVENT_NAME.str, event.name)
        jsonMessage.demandValue(Key.BEHOV.str, behov.name)
    }

    companion object {
        val packetValidator = River.PacketValidation {
            it.demandKey(Key.EVENT_NAME.str)
            it.demandKey(Key.BEHOV.str)
            it.rejectKey(Key.DATA.str)
            it.rejectKey(Key.LØSNING.str)
            it.rejectKey(Key.FAIL.str)
            it.interestedIn(Key.UUID.str)
            it.interestedIn(Key.FORESPOERSEL_ID.str)
        }
    }

    operator fun get(key: Key): JsonNode = jsonMessage[key.str]

    operator fun contains(key: Key): Boolean = jsonMessage[key.str].isMissingOrNull().not()

    fun createData(map: Map<Key, Any>): Data {
        val forespoerselID = jsonMessage[Key.FORESPOERSEL_ID.str]
        return Data(
            event,
            JsonMessage.newMessage(
                event.name,
                mapOfNotNull(
                    Key.DATA.str to "",
                    Key.UUID.str to this.uuid().takeUnless { it.isBlank() },
                    Key.FORESPOERSEL_ID.str to forespoerselID
                ) + map.mapKeys { it.key.str }
            )
        )
    }

    fun createFail(feilmelding: String): Fail {
        val json = jsonMessage.toJson().parseJson()
        return Fail(
            feilmelding = feilmelding,
            event = event,
            transaksjonId = json.toMap()[Key.UUID]
                ?.fromJson(UuidSerializer)
                .orDefault {
                    UUID.randomUUID().also {
                        sikkerLogger.error("Mangler transaksjonId i Behov. Erstatter med ny, tilfeldig UUID '$it'.\n${json.toPretty()}")
                    }
                },
            forespoerselId = forespoerselId?.takeUnless { it.isBlank() }
                ?.let(UUID::fromString)
                .also {
                    if (it == null) {
                        sikkerLogger.error("Mangler forespoerselId i Behov.\n${json.toPretty()}")
                    }
                },
            utloesendeMelding = json
        )
    }

    fun createBehov(behov: BehovType, data: Map<Key, Any>): Behov {
        return Behov(
            this.event,
            behov,
            forespoerselId,
            JsonMessage.newMessage(
                eventName = event.name,
                map = mapOfNotNull(
                    Key.BEHOV.str to behov.name,
                    Key.UUID.str to uuid().takeUnless { it.isBlank() },
                    Key.FORESPOERSEL_ID.str to this.forespoerselId
                ) + data.mapKeys { it.key.str }
            )
        )
    }

    fun uuid() = jsonMessage[Key.UUID.str].takeUnless { it.isMissingOrNull() }?.asText().orEmpty()
}
