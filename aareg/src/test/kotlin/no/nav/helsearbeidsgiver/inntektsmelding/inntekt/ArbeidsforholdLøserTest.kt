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
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.PublishedLøsning
import no.nav.helsearbeidsgiver.felles.test.mock.MockUuid
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.lastMessageJson
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.ArbeidsforholdLøser

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
            Key.BEHOV to expected.behov.toJson(BehovType::toJson),
            Key.ID to MockUuid.STRING.toJson(),
            Key.IDENTITETSNUMMER to expected.identitetsnummer.toJson()
        )

        val actual = testRapid.lastMessageJson().let(Published::fromJson)

        coVerifySequence { mockAaregClient.hentArbeidsforhold(expected.identitetsnummer, MockUuid.STRING) }
        testRapid.inspektør.size shouldBeExactly 1
        actual shouldBe expected
    }

    test("ved feil fra aareg så publiseres løsning med feilmelding") {
        val expected = Published.mockFailure()

        coEvery { mockAaregClient.hentArbeidsforhold(any(), any()) } throws RuntimeException()

        testRapid.sendJson(
            Key.BEHOV to expected.behov.toJson(BehovType::toJson),
            Key.ID to MockUuid.STRING.toJson(),
            Key.IDENTITETSNUMMER to expected.identitetsnummer.toJson()
        )

        val actual = testRapid.lastMessageJson().let(Published::fromJson)

        coVerifySequence { mockAaregClient.hentArbeidsforhold(expected.identitetsnummer, MockUuid.STRING) }
        testRapid.inspektør.size shouldBeExactly 1
        actual shouldBe expected
    }
})

@Serializable
@OptIn(ExperimentalSerializationApi::class)
private data class Published(
    @JsonNames("@behov")
    override val behov: List<BehovType>,
    @JsonNames("@løsning")
    override val løsning: Map<BehovType, ArbeidsforholdLøsning>,
    val identitetsnummer: String
) : PublishedLøsning<ArbeidsforholdLøsning> {
    companion object : PublishedLøsning.CompanionObj<Published>(Published::class) {
        private val behovType = BehovType.ARBEIDSFORHOLD

        override fun mockSuccess(): Published =
            Published(
                behov = behovType.let(::listOf),
                løsning = mapOf(
                    behovType to mockLøsningSuccess()
                ),
                identitetsnummer = "collide-levitator-modify"
            )

        override fun mockFailure(): Published =
            Published(
                behov = behovType.let(::listOf),
                løsning = mapOf(
                    behovType to mockLøsningFailure()
                ),
                identitetsnummer = "oil-probably-stack"
            )
    }
}
