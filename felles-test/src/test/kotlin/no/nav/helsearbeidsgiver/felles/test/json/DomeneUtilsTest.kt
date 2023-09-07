package no.nav.helsearbeidsgiver.felles.test.json

import io.kotest.assertions.throwables.shouldThrow
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Data
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Event
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Message
import no.nav.helsearbeidsgiver.utils.json.parseJson
import org.junit.jupiter.api.Test
import java.lang.RuntimeException
import java.util.UUID

class DomeneUtilsTest {

    @Test
    fun `Test JsonNode is Event`() {
        val event = Event.create(EventName.TRENGER_REQUESTED, "123", mapOf(DataFelt.VIRKSOMHET to "My virksomhet"))
        val message = event.toJsonMessage()
            .toJson()
            .parseJson()
            .toDomeneMessage<Message> {
                it.interestedIn(DataFelt.VIRKSOMHET)
            }
        assert(message is Event)
    }

    @Test
    fun `Test JsonNode is Behov`() {
        val event = Behov.create(EventName.TRENGER_REQUESTED, BehovType.FULLT_NAVN, "123", mapOf(DataFelt.VIRKSOMHET to "My virksomhet"))
        val message = event.toJsonMessage()
            .toJson()
            .parseJson()
            .toDomeneMessage<Message> {
                it.interestedIn(DataFelt.VIRKSOMHET)
            }
        assert(message is Behov)
    }

    @Test
    fun `Test JsonNode is Data`() {
        val event = Data.create(EventName.TRENGER_REQUESTED, UUID.randomUUID(), mapOf(DataFelt.VIRKSOMHET to "My virksomhet"))
        val message = event.toJsonMessage()
            .toJson()
            .parseJson()
            .toDomeneMessage<Message> {
                it.interestedIn(DataFelt.VIRKSOMHET)
            }
        assert(message is Data)
    }

    @Test
    fun `Test JsonNode is Fail`() {
        val event = Fail.create(EventName.TRENGER_REQUESTED, feilmelding = "Feilmelding", data = mapOf(DataFelt.VIRKSOMHET to "My virksomhet"))
        val message = event.toJsonMessage()
            .toJson()
            .parseJson()
            .toDomeneMessage<Message> {
                it.interestedIn(DataFelt.VIRKSOMHET)
            }
        assert(message is Fail)
    }

    @Test
    fun `Test JsonNode is something else`() {
        val illegalMessage = JsonMessage.newMessage(mapOf(Key.BEHOV.str to BehovType.FULLT_NAVN.name, Key.FAIL.str to "some fail"))
        val json = illegalMessage.toJson().parseJson()

        shouldThrow<MessageProblems.MessageException> {
            json.toDomeneMessage {
                it.interestedIn(DataFelt.VIRKSOMHET)
            }
        }
        shouldThrow<RuntimeException> {
            json.toDomeneMessage {
                it.interestedIn(DataFelt.VIRKSOMHET)
            }
        }
    }
}
