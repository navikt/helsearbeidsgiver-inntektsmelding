package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
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

class TrengerForespoerselLoeserTest : FunSpec({
    val testRapid = TestRapid()
    val mockPriProducer = mockk<PriProducer>()

    TrengerForespoerselLoeser(testRapid, mockPriProducer)

    test("Ved behov om forespørsel på rapid-topic publiseres behov om forespørsel på pri-topic") {
        // Må bare returnere en Result med gyldig JSON
        every { mockPriProducer.send(*anyVararg<Pair<Pri.Key, JsonElement>>()) } returns Result.success(JsonNull)

        val expectedEvent = EventName.INNTEKT_REQUESTED
        val expectedTransaksjonId = randomUuid()
        val expectedForespoerselId = randomUuid()

        testRapid.sendJson(
            Key.EVENT_NAME to expectedEvent.toJson(),
            Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
            Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
            Key.UUID to expectedTransaksjonId.toJson()
        )

        verifySequence {
            mockPriProducer.send(
                Pri.Key.BEHOV to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(Pri.BehovType.serializer()),
                Pri.Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
                Pri.Key.BOOMERANG to mapOf(
                    Key.EVENT_NAME to expectedEvent.toJson(),
                    Key.UUID to expectedTransaksjonId.toJson()
                ).toJson()
            )
        }
    }
})
