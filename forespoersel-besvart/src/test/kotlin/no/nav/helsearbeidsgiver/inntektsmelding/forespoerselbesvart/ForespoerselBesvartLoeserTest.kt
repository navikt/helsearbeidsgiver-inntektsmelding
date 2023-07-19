@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import java.util.UUID

class ForespoerselBesvartLoeserTest : FunSpec({
    val testRapid = TestRapid()
    val mockPriProducer = mockk<PriProducer<JsonElement>>(relaxed = true)

    ForespoerselBesvartLoeser(testRapid, mockPriProducer)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
    }

    test("Ved notis om besvart forespørsel publiseres behov om å hente notifikasjon-ID-er") {
        val expected = Published.mock()

        mockStatic(::randomUuid) {
            every { randomUuid() } returns expected.transaksjonId

            testRapid.sendJson(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to expected.forespoerselId.toJson()
            )
        }

        val actual = testRapid.firstMessage().fromJson(Published.serializer())

        testRapid.inspektør.size shouldBeExactly 1
        actual shouldBe expected
    }

    test("Ved feil så republiseres den innkommende meldingen") {
        val expectedRepublisert = mapOf(
            Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
            Pri.Key.FORESPOERSEL_ID to "ikke en uuid".toJson()
        )

        testRapid.sendJson(
            *expectedRepublisert.toList().toTypedArray()
        )

        testRapid.inspektør.size shouldBeExactly 0

        verifySequence {
            mockPriProducer.send(
                withArg {
                    it.fromJsonMapFiltered(Pri.Key.serializer()) shouldBe expectedRepublisert
                }
            )
        }
    }
})

@Serializable
private data class Published(
    @SerialName("@event_name")
    val eventName: EventName,
    @SerialName("@behov")
    val behov: BehovType,
    val forespoerselId: UUID,
    @SerialName("transaction_origin")
    val transaksjonId: UUID
) {
    companion object {
        fun mock(): Published =
            Published(
                eventName = EventName.FORESPOERSEL_BESVART,
                behov = BehovType.NOTIFIKASJON_HENT_ID,
                forespoerselId = UUID.randomUUID(),
                transaksjonId = UUID.randomUUID()
            )
    }
}
