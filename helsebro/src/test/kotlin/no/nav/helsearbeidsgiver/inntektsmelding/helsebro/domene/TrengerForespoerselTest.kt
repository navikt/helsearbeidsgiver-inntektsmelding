package no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.mockTrengerForespoersel

class TrengerForespoerselTest : FunSpec({
    test("data serialiseres korrekt") {
        val trengerForespoersel = mockTrengerForespoersel()

        val expectedJson = """
            {
                "${Pri.Key.FORESPOERSEL_ID}": "${trengerForespoersel.forespoerselId}",
                "${Pri.Key.BOOMERANG}": ${trengerForespoersel.boomerang},
                "${Pri.Key.BEHOV}": "${trengerForespoersel.behov}"
            }
        """.removeJsonWhitespace()

        val actualJson = trengerForespoersel.toJson(TrengerForespoersel.serializer()).toString()

        actualJson shouldBe expectedJson
    }
})
