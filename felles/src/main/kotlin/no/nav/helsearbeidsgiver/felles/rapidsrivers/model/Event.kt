package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.TxMessage
import no.nav.helsearbeidsgiver.felles.utils.mapOfNotNull
import no.nav.helsearbeidsgiver.utils.json.parseJson
import java.util.UUID

class Event(
    val event: EventName,
    val forespoerselId: String? = null,
    val jsonMessage: JsonMessage,
    val clientId: String? = null
) : TxMessage {

    @Transient
    var uuid: String? = null

    init {
        packetValidator.validate(jsonMessage)
        jsonMessage.demandValue(Key.EVENT_NAME.str, event.name)
    }

    companion object {
        val packetValidator = River.PacketValidation {
            it.demandKey(Key.EVENT_NAME.str)
            it.rejectKey(Key.BEHOV.str)
            it.rejectKey(Key.DATA.str)
            it.rejectKey(Key.FAIL.str)
            it.rejectKey(Key.LØSNING.str)
            // midlertidig, generelt det bør vare reject
            it.interestedIn(Key.UUID.str)
            it.interestedIn(Key.TRANSACTION_ORIGIN.str)
            it.interestedIn(Key.CLIENT_ID.str)
            it.interestedIn(Key.FORESPOERSEL_ID.str)
        }

        fun create(event: EventName, forespoerselId: String?, map: Map<IKey, Any> = emptyMap()): Event {
            return Event(
                event,
                forespoerselId,
                JsonMessage.newMessage(event.name, mapOfNotNull(Key.FORESPOERSEL_ID.str to forespoerselId) + map.mapKeys { it.key.str })
            )
        }
    }

    fun createBehov(behov: BehovType, map: Map<DataFelt, Any>): Behov {
        val forespoerselID = jsonMessage[Key.FORESPOERSEL_ID.str].takeUnless { it.isMissingOrNull() }
        uuid = uuid ?: UUID.randomUUID().toString()
        return Behov(
            event,
            behov,
            this.forespoerselId,
            JsonMessage.newMessage(
                event.name,
                mapOfNotNull(
                    Key.BEHOV.str to behov.name,
                    Key.UUID.str to this.uuid,
                    Key.FORESPOERSEL_ID.str to forespoerselID
                ) + map.mapKeys { it.key.str }
            )
        )
    }

    fun createFail(feilmelding: String): Fail =
        Fail(
            feilmelding = feilmelding,
            event = event,
            transaksjonId = uuid().takeUnless { it.isBlank() }?.let(UUID::fromString),
            forespoerselId = forespoerselId?.takeUnless { it.isBlank() }?.let(UUID::fromString),
            utloesendeMelding = jsonMessage.toJson().parseJson()
        )

    override fun uuid() = this.uuid.orEmpty()
}
