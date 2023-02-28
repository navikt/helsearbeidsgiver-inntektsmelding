package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.kotest.core.spec.style.FunSpec
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
            Key.BEHOV to listOf(BehovType.HENT_TRENGER_IM).toJson(BehovType.serializer()),
            Key.FORESPOERSEL_ID to expectedPublished.forespoerselId.toJson(),
            Key.BOOMERANG to expectedPublished.boomerang
        )

        verifySequence {
            mockPriProducer.send(expectedPublished)
        }
    }
})
