package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson

class ForespoerselBesvartFraSimbaLoeserTest : FunSpec({
    val testRapid = TestRapid()

    ForespoerselBesvartFraSimbaLoeser(testRapid)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
    }

    test("Ved mottatt inntektsmelding publiseres behov om å hente notifikasjon-ID-er") {
        val expected = Published.mock()

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.FORESPOERSEL_ID to expected.forespoerselId.toJson(),
            Key.UUID to expected.transaksjonId.toJson()
        )

        val actual = testRapid.firstMessage().fromJson(Published.serializer())

        testRapid.inspektør.size shouldBeExactly 1
        actual shouldBe expected
    }

    test("Ved feil så republiseres _ikke_ den innkommende meldingen") {
        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.FORESPOERSEL_ID to "ikke en uuid".toJson(),
            Key.UUID to "heller ikke en uuid".toJson()
        )

        testRapid.inspektør.size shouldBeExactly 0
    }
})
