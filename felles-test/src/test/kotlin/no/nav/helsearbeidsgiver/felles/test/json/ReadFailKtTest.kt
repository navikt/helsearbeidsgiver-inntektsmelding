package no.nav.helsearbeidsgiver.felles.test.json

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.utils.json.parseJson
import org.junit.jupiter.api.Test

class ReadFailKtTest {
    @Test
    fun `Test JsonNode is Fail`() {
        val event = Fail.create(EventName.TRENGER_REQUESTED, feilmelding = "Feilmelding", data = mapOf(DataFelt.VIRKSOMHET to "My virksomhet"))
        shouldNotThrowAny {
            event.jsonMessage
                .toJson()
                .parseJson()
                .readFail()
        }
    }

    @Test
    fun `Test JsonNode is something else`() {
        val illegalMessage = JsonMessage.newMessage(mapOf(Key.BEHOV.str to BehovType.FULLT_NAVN.name, Key.FAIL.str to "some fail"))
        val json = illegalMessage.toJson().parseJson()

        shouldThrow<MessageProblems.MessageException> {
            json.readFail()
        }
        shouldThrow<RuntimeException> {
            json.readFail()
        }
    }
}
