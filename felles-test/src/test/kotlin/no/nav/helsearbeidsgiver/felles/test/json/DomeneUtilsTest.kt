package no.nav.helsearbeidsgiver.felles.test.json

import com.fasterxml.jackson.databind.JsonNode
import io.kotest.assertions.throwables.shouldThrow
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Data
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Event
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Message
import org.junit.jupiter.api.Test
import java.lang.RuntimeException

class DomeneUtilsTest {

    @Test
    fun `Test JsonNode is Event`() {
        val event = Event.create(EventName.TRENGER_REQUESTED, "123", mapOf(DataFelt.VIRKSOMHET to "My virksomhet"))
        val jsonNode: JsonNode = customObjectMapper().readTree(event.toJsonMessage().toJson())
        val message: Message = jsonNode.toDomeneMessage {
            it.interestedIn(DataFelt.VIRKSOMHET)
        }
        assert(message is Event)
    }

    @Test
    fun `Test JsonNode is Behov`() {
        val event = Behov.create(EventName.TRENGER_REQUESTED, BehovType.FULLT_NAVN, "123", mapOf(DataFelt.VIRKSOMHET to "My virksomhet"))
        val jsonNode: JsonNode = customObjectMapper().readTree(event.toJsonMessage().toJson())
        val message: Message = jsonNode.toDomeneMessage {
            it.interestedIn(DataFelt.VIRKSOMHET)
        }
        assert(message is Behov)
    }

    @Test
    fun `Test JsonNode is Data`() {
        val event = Data.create(EventName.TRENGER_REQUESTED, mapOf(DataFelt.VIRKSOMHET to "My virksomhet"))
        val jsonNode: JsonNode = customObjectMapper().readTree(event.toJsonMessage().toJson())
        val message: Message = jsonNode.toDomeneMessage {
            it.interestedIn(DataFelt.VIRKSOMHET)
        }
        assert(message is Data)
    }

    @Test
    fun `Test JsonNode is Fail`() {
        val event = Fail.create(EventName.TRENGER_REQUESTED, feilmelding = "Feilmelding", data = mapOf(DataFelt.VIRKSOMHET to "My virksomhet"))
        val jsonNode: JsonNode = customObjectMapper().readTree(event.toJsonMessage().toJson())
        val message: Message = jsonNode.toDomeneMessage {
            it.interestedIn(DataFelt.VIRKSOMHET)
        }
        assert(message is Fail)
    }

    @Test
    fun `Test JsonNode is something else`() {
        val illegalMessage = JsonMessage.newMessage(mapOf(Key.BEHOV.str to BehovType.FULLT_NAVN.name, Key.FAIL.str to "some fail"))
        val jsonNode: JsonNode = customObjectMapper().readTree(illegalMessage.toJson())

        shouldThrow<MessageProblems.MessageException> {
            jsonNode.toDomeneMessage {
                it.interestedIn(DataFelt.VIRKSOMHET)
            }
        }
        val extremlyIllegalMessage = JsonMessage.newMessage(mapOf(Key.TRANSACTION_ORIGIN.str to "123"))
        shouldThrow<RuntimeException> {
            jsonNode.toDomeneMessage {
                it.interestedIn(DataFelt.VIRKSOMHET)
            }
        }
    }
}
