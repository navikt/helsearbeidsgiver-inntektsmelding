@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.serialization.UseSerializers
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Data
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.json.toDomeneMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.ArbeidsforholdLøser
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.tilArbeidsforhold
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class ArbeidsforholdLøserTest : FunSpec({
    val testRapid = TestRapid()

    val mockAaregClient = mockk<AaregClient>()

    ArbeidsforholdLøser(testRapid, mockAaregClient)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
    }

    test("ved innkommende behov så hentes og publiseres arbeidsforhold fra aareg") {
        val expected = Data.create(
            EventName.INSENDING_STARTED,
            UUID.randomUUID(),
            mapOf(
                DataFelt.ARBEIDSFORHOLD to no.nav.helsearbeidsgiver.felles.Data(
                    mockKlientArbeidsforhold().tilArbeidsforhold().let(::listOf)
                )
            )
        ).toJsonMessage()
            .also {
                Data.packetValidator.validate(it)
                it.interestedIn(DataFelt.ARBEIDSFORHOLD.str)
            }.let {
                Data.create(it)
            }

        coEvery { mockAaregClient.hentArbeidsforhold(any(), any()) } returns mockKlientArbeidsforhold().let(::listOf)

        testRapid.sendJson(
            Key.EVENT_NAME to expected.event.toJson(),
            Key.BEHOV to BehovType.ARBEIDSFORHOLD.toJson(),
            Key.IDENTITETSNUMMER to Mock.FNR.toJson(),
            Key.UUID to expected.uuid().toJson()
        )

        val actual = testRapid.firstMessage().toJsonNode().toDomeneMessage<Data>() {
            it.interestedIn(DataFelt.ARBEIDSFORHOLD.str)
        }

        coVerifySequence { mockAaregClient.hentArbeidsforhold(Mock.FNR, expected.uuid()) }
        testRapid.inspektør.size shouldBeExactly 1
        actual.uuid() shouldBe expected.uuid()
        actual.event shouldBe expected.event
        actual[DataFelt.ARBEIDSFORHOLD] shouldBe expected[DataFelt.ARBEIDSFORHOLD]
    }

    test("ved feil fra aareg så publiseres løsning med feilmelding") {
        val uuid = UUID.randomUUID()
        val expected = Fail.create(
            EventName.TRENGER_REQUESTED,
            BehovType.ARBEIDSFORHOLD,
            "Klarte ikke hente arbeidsforhold",
            uuid.toString()
        )

        coEvery { mockAaregClient.hentArbeidsforhold(any(), any()) } throws RuntimeException()

        testRapid.sendJson(
            Key.EVENT_NAME to expected.event.toJson(),
            Key.BEHOV to BehovType.ARBEIDSFORHOLD.toJson(),
            Key.IDENTITETSNUMMER to Mock.FNR.toJson(),
            Key.UUID to expected.uuid!!.toJson()
        )

        val actual = testRapid.firstMessage().toJsonNode().toDomeneMessage<Fail>()

        coVerifySequence { mockAaregClient.hentArbeidsforhold(Mock.FNR, expected.uuid.toString()) }
        testRapid.inspektør.size shouldBeExactly 1
        actual.uuid() shouldBe expected.uuid()
        actual.behov shouldBe expected.behov
        actual.feilmelding shouldBe expected.feilmelding
    }
})

private object Mock {
    const val FNR = "12121200012"
}
