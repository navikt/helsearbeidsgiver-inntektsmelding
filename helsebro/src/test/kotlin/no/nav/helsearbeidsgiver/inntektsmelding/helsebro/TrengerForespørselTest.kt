package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import no.nav.helsearbeidsgiver.felles.test.json.removeJsonWhitespace
import java.util.UUID

class TrengerForespørselTest : FunSpec({
    test("data serialiseres korrekt") {
        val trengerForespørsel = TrengerForespørsel(
            orgnr = "123",
            fnr = "abc",
            UUID.randomUUID()
        )

        val expectedJson = """
            {
                "orgnr": "${trengerForespørsel.orgnr}",
                "fnr": "${trengerForespørsel.fnr}",
                "vedtaksperiodeId": "${trengerForespørsel.vedtaksperiodeId}",
                "eventType": "${trengerForespørsel.eventType}"
            }
        """.removeJsonWhitespace()

        val actualJson = trengerForespørsel.toJson()

        actualJson shouldBeEqualComparingTo expectedJson
    }
})
