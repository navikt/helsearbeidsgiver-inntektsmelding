@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

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
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import java.util.UUID

class ForespoerselMottattLoeserTest : FunSpec({
    val testRapid = TestRapid()
    val mockPriProducer = mockk<PriProducer>(relaxed = true)

    ForespoerselMottattLoeser(testRapid, mockPriProducer)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
    }

    test("Ved notis om mottatt forespørsel publiseres behov om notifikasjon") {
        val expected = Published.mock()

        mockStatic(::randomUuid) {
            every { randomUuid() } returns expected.transaksjonId

            testRapid.sendJson(
                Pri.Key.NOTIS to Pri.NotisType.FORESPØRSEL_MOTTATT.toJson(Pri.NotisType.serializer()),
                Pri.Key.ORGNR to expected.orgnrUnderenhet.toJson(),
                Pri.Key.FNR to expected.identitetsnummer.toJson(),
                Pri.Key.FORESPOERSEL_ID to expected.forespoerselId.toJson()
            )
        }

        val actual = testRapid.firstMessage().fromJson(Published.serializer())

        testRapid.inspektør.size shouldBeExactly 1
        actual shouldBe expected
    }

    test("Ved feil så republiseres den innkommende meldingen") {
        val expectedRepublisert = mapOf(
            Pri.Key.NOTIS to Pri.NotisType.FORESPØRSEL_MOTTATT.toJson(Pri.NotisType.serializer()),
            Pri.Key.ORGNR to "hyper-oval".toJson(),
            Pri.Key.FNR to "trett-fjell".toJson(),
            Pri.Key.FORESPOERSEL_ID to "ikke en uuid".toJson()
        )

        testRapid.sendJson(
            *expectedRepublisert.toList().toTypedArray()
        )

        testRapid.inspektør.size shouldBeExactly 0

        verifySequence {
            mockPriProducer.send(
                withArg<JsonElement> {
                    it.toMap() shouldBe expectedRepublisert
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
    val orgnrUnderenhet: String,
    val identitetsnummer: String,
    val forespoerselId: UUID,
    @SerialName("uuid")
    val transaksjonId: UUID
) {
    companion object {
        fun mock(): Published =
            Published(
                eventName = EventName.FORESPØRSEL_MOTTATT,
                behov = BehovType.LAGRE_FORESPOERSEL,
                orgnrUnderenhet = "certainly-stereo-facsimile",
                identitetsnummer = "resort-cringe-huddle",
                forespoerselId = UUID.randomUUID(),
                transaksjonId = UUID.randomUUID()
            )
    }
}
