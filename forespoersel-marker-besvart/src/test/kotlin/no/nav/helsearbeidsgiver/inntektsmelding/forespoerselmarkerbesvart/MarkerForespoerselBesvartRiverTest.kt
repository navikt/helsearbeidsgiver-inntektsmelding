package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.toJson

class MarkerForespoerselBesvartRiverTest : FunSpec({
    val testRapid = TestRapid()
    val mockPriProducer = mockk<PriProducer>()

    MarkerForespoerselBesvartRiver(testRapid, mockPriProducer)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
    }

    test("Ved event om mottatt inntektsmelding på rapid-topic publiseres notis om å markere forespørsel som besvart på pri-topic") {
        // Må bare returnere en Result med gyldig JSON
        every { mockPriProducer.send(*anyVararg<Pair<Pri.Key, JsonElement>>()) } returns Result.success(JsonNull)

        val expectedForespoerselId = randomUuid()

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.UUID to randomUuid().toJson(),
            Key.FORESPOERSEL_ID to expectedForespoerselId.toJson()
        )

        testRapid.inspektør.size shouldBeExactly 0

        verifySequence {
            mockPriProducer.send(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART_SIMBA.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to expectedForespoerselId.toJson()
            )
        }
    }

    test("Ignorerer meldinger med behov") {
        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.UUID to randomUuid().toJson(),
            Key.FORESPOERSEL_ID to randomUuid().toJson(),
            Key.BEHOV to BehovType.FULLT_NAVN.toJson()
        )

        testRapid.inspektør.size shouldBeExactly 0

        verify(exactly = 0) {
            mockPriProducer.send(*anyVararg<Pair<Pri.Key, JsonElement>>())
        }
    }

    test("Ignorerer meldinger med data") {
        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.UUID to randomUuid().toJson(),
            Key.FORESPOERSEL_ID to randomUuid().toJson(),
            Key.DATA to "".toJson()
        )

        testRapid.inspektør.size shouldBeExactly 0

        verify(exactly = 0) {
            mockPriProducer.send(*anyVararg<Pair<Pri.Key, JsonElement>>())
        }
    }
})
