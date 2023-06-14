package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.felles.ArbeidsforholdLøsning
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.test.mock.MockUuid
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.ArbeidsforholdLøser
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson

class ArbeidsforholdLøserTest : FunSpec({
    val testRapid = TestRapid()

    val mockAaregClient = mockk<AaregClient>()

    ArbeidsforholdLøser(testRapid, mockAaregClient)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
    }

    test("ved innkommende behov så hentes og publiseres arbeidsforhold fra aareg") {
        val expected = Published.mockSuccess()

        coEvery { mockAaregClient.hentArbeidsforhold(any(), any()) } returns mockKlientArbeidsforhold().let(::listOf)

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(EventName.serializer()),
            Key.BEHOV to expected.behov.toJson(BehovType.serializer()),
            Key.ID to MockUuid.STRING.toJson(),
            Key.UUID to "uuid".toJson(),
            Key.IDENTITETSNUMMER to expected.identitetsnummer.toJson(),
            Key.FORESPOERSEL_ID to "123456".toJson()
        )

        val actual = testRapid.firstMessage().fromJson(Published.serializer())

        coVerifySequence { mockAaregClient.hentArbeidsforhold(expected.identitetsnummer, MockUuid.STRING) }
        testRapid.inspektør.size shouldBeExactly 2
        actual shouldBe expected
    }

    test("ved feil fra aareg så publiseres løsning med feilmelding") {
        val expected = Published.mockFailure()

        coEvery { mockAaregClient.hentArbeidsforhold(any(), any()) } throws RuntimeException()

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(EventName.serializer()),
            Key.BEHOV to expected.behov.toJson(BehovType.serializer()),
            Key.ID to MockUuid.STRING.toJson(),
            Key.UUID to "uuiid".toJson(),
            Key.IDENTITETSNUMMER to expected.identitetsnummer.toJson(),
            Key.FORESPOERSEL_ID to "123456".toJson()
        )

        val actual = testRapid.firstMessage().fromJson(Published.serializer())

        coVerifySequence { mockAaregClient.hentArbeidsforhold(expected.identitetsnummer, MockUuid.STRING) }
        testRapid.inspektør.size shouldBeExactly 2
        actual shouldBe expected
    }
})

@Serializable
@OptIn(ExperimentalSerializationApi::class)
private data class Published(
    @JsonNames("@behov")
    val behov: List<BehovType>,
    @JsonNames("@løsning")
    val løsning: Map<BehovType, ArbeidsforholdLøsning>,
    val identitetsnummer: String
) {
    companion object {
        private val behovType = BehovType.ARBEIDSFORHOLD

        fun mockSuccess(): Published =
            Published(
                behov = behovType.let(::listOf),
                løsning = mapOf(
                    behovType to mockLøsningSuccess()
                ),
                identitetsnummer = "collide-levitator-modify"
            )

        fun mockFailure(): Published =
            Published(
                behov = behovType.let(::listOf),
                løsning = mapOf(
                    behovType to mockLøsningFailure()
                ),
                identitetsnummer = "oil-probably-stack"
            )
    }
}
