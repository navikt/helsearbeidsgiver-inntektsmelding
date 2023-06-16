package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.contains
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.felles.test.mock.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InnsendingServiceIT : EndToEndTest() {

    @Test
    fun `Test at innsnending er mottatt`() {
        val forespoerselId = UUID.randomUUID().toString()
        val clientId = UUID.randomUUID()
        forespoerselRepository.lagreForespørsel(forespoerselId, TestData.validOrgNr)

        publish(
            mapOf(
                Key.EVENT_NAME.str to EventName.INSENDING_STARTED.name,
                Key.CLIENT_ID.str to clientId,
                DataFelt.INNTEKTSMELDING.str to GYLDIG_INNSENDING_REQUEST,
                DataFelt.ORGNRUNDERENHET.str to TestData.validOrgNr,
                Key.IDENTITETSNUMMER.str to TestData.validIdentitetsnummer,
                Key.FORESPOERSEL_ID.str to forespoerselId
            )
        )

        Thread.sleep(10000)

        println("\nAlle meldinger:\n-----------------------")
        messages.all().forEach { println(it) }
        println("-----------------------")
        println("\nAlle filtrerte meldinger for client-ID $clientId:\n-----------------------")
        val filteredMessages = messages.all().map(JsonElement::toJsonNode).filter(clientId)
        filteredMessages.forEach { println(it.toPrettyString()) }
        println("-----------------------")

        val messageCount = filteredMessages.size - 1
        assertEquals(9, messageCount) {
            "Message count was $messageCount"
        }
        val innsendingStr = redisStore.get(clientId.toString())
        assertTrue(innsendingStr?.length!! > 2)
    }
}

private fun List<JsonNode>.filter(clientId: UUID): List<JsonNode> {
    var transaksjonId: String? = null
    return filter {
        if (it.contains(Key.CLIENT_ID.str)) {
            assertEquals(clientId.toString(), it[Key.CLIENT_ID.str].asText())
            true
        } else {
            val eventName = it.get(Key.EVENT_NAME.str).asText()
            if (transaksjonId == null && (eventName == EventName.INSENDING_STARTED.name && it.contains(Key.BEHOV.str))) {
                transaksjonId = it[Key.UUID.str].asText()
            }
            val msgUuid = if (it.contains(Key.UUID.str)) it.get(Key.UUID.str).asText() else it.get(Key.TRANSACTION_ORIGIN.str).asText()
            msgUuid == transaksjonId &&
                (eventName == EventName.INSENDING_STARTED.name || (eventName == EventName.INNTEKTSMELDING_MOTTATT.name && !it.has(Key.BEHOV.str))) &&
                !it.has(Key.LØSNING.str)
        }
    }
}
