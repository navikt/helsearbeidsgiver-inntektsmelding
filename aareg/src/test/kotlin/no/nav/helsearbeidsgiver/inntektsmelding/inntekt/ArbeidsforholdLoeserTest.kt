@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Data
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toJsonMap
import no.nav.helsearbeidsgiver.felles.test.json.readFail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.ArbeidsforholdLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.tilArbeidsforhold
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class ArbeidsforholdLoeserTest : FunSpec({
    val testRapid = TestRapid()

    val mockAaregClient = mockk<AaregClient>()

    ArbeidsforholdLoeser(testRapid, mockAaregClient)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
    }

    test("ved innkommende behov så hentes og publiseres arbeidsforhold fra aareg") {
        val expectedUuid = UUID.randomUUID()

        val expected = Data(
            event = EventName.INSENDING_STARTED,
            jsonMessage = JsonMessage.newMessage(
                EventName.INSENDING_STARTED.name,
                mapOf(
                    Key.DATA.str to "",
                    Key.UUID.str to expectedUuid.toString(),
                    Key.ARBEIDSFORHOLD.str to no.nav.helsearbeidsgiver.felles.Data(
                        mockKlientArbeidsforhold().tilArbeidsforhold().let(::listOf).toJson(Arbeidsforhold.serializer()).toJsonNode()
                    )
                )
            )
        )
            .jsonMessage
            .toJsonMap()

        coEvery { mockAaregClient.hentArbeidsforhold(any(), any()) } returns mockKlientArbeidsforhold().let(::listOf)

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.BEHOV to BehovType.ARBEIDSFORHOLD.toJson(),
            Key.IDENTITETSNUMMER to Mock.FNR.toJson(),
            Key.UUID to expectedUuid.toJson()
        )

        val actual = testRapid.firstMessage().toMap()

        coVerifySequence { mockAaregClient.hentArbeidsforhold(Mock.FNR, expectedUuid.toString()) }
        testRapid.inspektør.size shouldBeExactly 1

        actual[Key.UUID]?.fromJson(UuidSerializer) shouldBe expectedUuid
        actual[Key.EVENT_NAME]?.fromJson(EventName.serializer()) shouldBe EventName.INSENDING_STARTED
        actual[Key.ARBEIDSFORHOLD] shouldBe expected[Key.ARBEIDSFORHOLD]
    }

    test("ved feil fra aareg så publiseres løsning med feilmelding") {
        val event = EventName.TRENGER_REQUESTED
        val transaksjonId = UUID.randomUUID()
        val forespoerselId = UUID.randomUUID()

        val innkommendeMelding = mapOf(
            Key.EVENT_NAME to event.toJson(),
            Key.BEHOV to BehovType.ARBEIDSFORHOLD.toJson(),
            Key.IDENTITETSNUMMER to Mock.FNR.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson()
        )

        val expected = Fail(
            feilmelding = "Klarte ikke hente arbeidsforhold",
            event = event,
            transaksjonId = transaksjonId,
            forespoerselId = forespoerselId,
            utloesendeMelding = innkommendeMelding.toJson(
                MapSerializer(
                    Key.serializer(),
                    JsonElement.serializer()
                )
            )
        )

        coEvery { mockAaregClient.hentArbeidsforhold(any(), any()) } throws RuntimeException()

        testRapid.sendJson(
            *innkommendeMelding.toList().toTypedArray()
        )

        coVerifySequence { mockAaregClient.hentArbeidsforhold(Mock.FNR, expected.transaksjonId.toString()) }

        testRapid.inspektør.size shouldBeExactly 1

        val fail = testRapid.firstMessage().readFail()

        fail.shouldBeEqualToIgnoringFields(expected, Fail::utloesendeMelding)
        fail.utloesendeMelding.fromJsonMapFiltered(Key.serializer()) shouldContainAll innkommendeMelding
    }
})

private object Mock {
    const val FNR = "12121200012"
}
