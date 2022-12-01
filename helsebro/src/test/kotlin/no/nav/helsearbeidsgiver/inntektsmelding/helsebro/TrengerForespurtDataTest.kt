package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import java.util.UUID

class TrengerForespurtDataTest : FunSpec({
    test("data serialiseres korrekt") {
        val trengerForespurtData = TrengerForespurtData(
            orgnr = "123",
            fnr = "abc",
            UUID.randomUUID()
        )

        val expectedJson = """
            {
                "orgnr": "${trengerForespurtData.orgnr}",
                "fnr": "${trengerForespurtData.fnr}",
                "vedtaksperiodeId": "${trengerForespurtData.vedtaksperiodeId}",
                "eventType": "${trengerForespurtData.eventType}"
            }
        """.removeJsonWhitespace()

        val actualJson = trengerForespurtData.toJson()

        actualJson shouldBeEqualComparingTo expectedJson
    }
})
