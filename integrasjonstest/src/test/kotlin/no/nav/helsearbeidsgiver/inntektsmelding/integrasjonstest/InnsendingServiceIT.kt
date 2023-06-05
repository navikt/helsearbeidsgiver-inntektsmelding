package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.contains
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.felles.test.mock.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InnsendingServiceIT : EndToEndTest() {

    @Test
    fun `Test at innsnending er mottatt`() {
        val forespoerselId = UUID.randomUUID().toString()
        val transaksjonsId = UUID.randomUUID()
        forespoerselRepository.lagreForespørsel(forespoerselId, TestData.validOrgNr)

        publish(
            mapOf(
                Key.EVENT_NAME.str to EventName.INSENDING_STARTED.name,
                Key.UUID.str to transaksjonsId,
                Key.INNTEKTSMELDING.str to GYLDIG_INNSENDING_REQUEST,
                Key.ORGNRUNDERENHET.str to TestData.validOrgNr,
                Key.IDENTITETSNUMMER.str to TestData.validIdentitetsnummer,
                Key.FORESPOERSEL_ID.str to forespoerselId
            )
        )

        Thread.sleep(10000)

        val filteredMessages = messages.all().map(JsonElement::toJsonNode).filter(transaksjonsId)

        val messageCount = filteredMessages.size - 1
        assertEquals(messageCount, 9) {
            "Message count was $messageCount"
        }
    }
}

private fun List<JsonNode>.filter(transaksjonId: UUID): List<JsonNode> =
    filter {
        val eventName = it.get(Key.EVENT_NAME.str).asText()
        val msgUuid = if (it.contains(Key.UUID.str)) it.get(Key.UUID.str).asText() else it.get(Key.TRANSACTION_ORIGIN.str).asText()
        msgUuid == transaksjonId.toString() && (
            eventName == EventName.INSENDING_STARTED.name ||
                (eventName == EventName.INNTEKTSMELDING_MOTTATT.name && !it.has(Key.BEHOV.str))
            ) && !it.has(Key.LØSNING.str)
    }
