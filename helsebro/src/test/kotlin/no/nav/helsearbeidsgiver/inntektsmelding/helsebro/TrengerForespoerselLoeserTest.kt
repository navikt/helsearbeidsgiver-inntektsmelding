package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.TrengerForespoersel
import no.nav.helsearbeidsgiver.utils.json.toJson

class TrengerForespoerselLoeserTest : FunSpec({
    val testRapid = TestRapid()
    val mockPriProducer = mockk<PriProducer<TrengerForespoersel>>(relaxed = true)

    TrengerForespoerselLoeser(testRapid, mockPriProducer)

    test("Ved behov om forespørsel på rapid-topic publiseres behov om forespørsel på pri-topic") {
        val expectedPublished = mockTrengerForespoersel()

        val boomerang = expectedPublished.boomerang.fromJsonMapOnlyKeys()

        val event = boomerang[Key.EVENT_NAME].shouldNotBeNull()
        val transaksjonId = boomerang[Key.UUID].shouldNotBeNull()

        testRapid.sendJson(
            Key.EVENT_NAME to event,
            Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
            Key.FORESPOERSEL_ID to expectedPublished.forespoerselId.toJson(),
            Key.UUID to transaksjonId
        )

        verifySequence {
            mockPriProducer.send(expectedPublished)
        }
    }
})
