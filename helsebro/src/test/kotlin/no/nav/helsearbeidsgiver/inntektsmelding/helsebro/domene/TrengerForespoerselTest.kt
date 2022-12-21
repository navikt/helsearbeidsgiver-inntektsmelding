package no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.mockTrengerForespoersel

class TrengerForespoerselTest : FunSpec({
    test("data serialiseres korrekt") {
        val trengerForespoersel = mockTrengerForespoersel()

        val expectedJson = """
            {
                "${Pri.Key.BEHOV}": "${trengerForespoersel.behov}",
                "${Pri.Key.ORGNR}": "${trengerForespoersel.orgnr}",
                "${Pri.Key.FNR}": "${trengerForespoersel.fnr}",
                "${Pri.Key.VEDTAKSPERIODE_ID}": "${trengerForespoersel.vedtaksperiodeId}",
                "${Pri.Key.BOOMERANG}": ${trengerForespoersel.boomerang.toJson()}
            }
        """.removeJsonWhitespace()

        val actualJson = trengerForespoersel.toJson().toString()

        actualJson shouldBeEqualComparingTo expectedJson
    }
})
