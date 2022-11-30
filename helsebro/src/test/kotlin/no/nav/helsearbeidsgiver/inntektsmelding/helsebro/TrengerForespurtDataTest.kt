package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo

class TrengerForespurtDataTest : FunSpec({
    test("data serialiseres korrekt") {
        val trengerForespurtData = TrengerForespurtData(
            orgnr = "123",
            fnr = "abc"
        )

        val expectedJson = """
            {
                "orgnr": "${trengerForespurtData.orgnr}",
                "fnr": "${trengerForespurtData.fnr}",
                "eventType": "${trengerForespurtData.eventType}"
            }
        """.removeJsonWhitespace()

        val actualJson = trengerForespurtData.toJson()

        actualJson shouldBeEqualComparingTo expectedJson
    }
})
