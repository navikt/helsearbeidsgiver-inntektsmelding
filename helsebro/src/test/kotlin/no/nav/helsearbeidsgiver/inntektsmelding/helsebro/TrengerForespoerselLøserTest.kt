package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson

class TrengerForespoerselLøserTest : FunSpec({
    val testRapid = TestRapid()
    val mockPriProducer = mockk<PriProducer>(relaxed = true)

    TrengerForespoerselLøser(testRapid, mockPriProducer)

    test("Ved behov om forespørsel på rapid-topic publiseres behov om forespørsel på pri-topic") {
        val expectedPublished = mockTrengerForespoersel()

        testRapid.sendJson(
            Key.BEHOV to listOf(BehovType.HENT_TRENGER_IM).toJson(BehovType::toJson),
            Key.UUID to expectedPublished.boomerang[Key.INITIATE_ID].shouldNotBeNull(),
            Key.FORESPOERSEL_ID to expectedPublished.forespoerselId.toJson()
        )

        verifySequence {
            mockPriProducer.send(expectedPublished)
        }
    }
})
