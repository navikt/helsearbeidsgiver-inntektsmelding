package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid

class HelsebroLøserTest : FunSpec({

    val testRapid = TestRapid()
    val mockPriProducer = mockk<PriProducer>(relaxed = true)

    HelsebroLøser(testRapid, mockPriProducer)

    test("Løser mottar melding om mottatt forespørsel") {
        val expectedTrengerForespurtData = TrengerForespurtData(
            "123",
            "abc"
        )

        testRapid.sendJson(
            "eventType" to "FORESPØRSEL_MOTTATT",
            "orgnr" to expectedTrengerForespurtData.orgnr,
            "fnr" to expectedTrengerForespurtData.fnr
        )

        verifySequence {
            mockPriProducer.send(expectedTrengerForespurtData)
        }
    }
})

private fun TestRapid.sendJson(vararg pair: Pair<String, String>) {
    pair.toMap()
        .let(JsonMessage::newMessage)
        .toJson()
        .let(this::sendTestMessage)
}
