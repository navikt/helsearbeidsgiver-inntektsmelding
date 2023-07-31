@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonNames
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.felles.ArbeidsforholdLøsning
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.ArbeidsforholdLøser
import no.nav.helsearbeidsgiver.utils.json.fromJson
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
        val expected = Published.mockSuccess()

        coEvery { mockAaregClient.hentArbeidsforhold(any(), any()) } returns mockKlientArbeidsforhold().let(::listOf)

        testRapid.sendJson(
            Key.EVENT_NAME to expected.eventName.toJson(),
            Key.BEHOV to expected.behov.toJson(),
            Key.IDENTITETSNUMMER to expected.identitetsnummer.toJson(),
            Key.UUID to expected.uuid.toJson()
        )

        val actual = testRapid.firstMessage().fromJson(Published.serializer())

        coVerifySequence { mockAaregClient.hentArbeidsforhold(expected.identitetsnummer, expected.uuid.toString()) }
        testRapid.inspektør.size shouldBeExactly 2
        actual shouldBe expected
    }

    test("ved feil fra aareg så publiseres løsning med feilmelding") {
        val expected = Published.mockFailure()

        coEvery { mockAaregClient.hentArbeidsforhold(any(), any()) } throws RuntimeException()

        testRapid.sendJson(
            Key.EVENT_NAME to expected.eventName.toJson(),
            Key.BEHOV to expected.behov.toJson(),
            Key.IDENTITETSNUMMER to expected.identitetsnummer.toJson(),
            Key.UUID to expected.uuid.toJson()
        )

        val actual = testRapid.firstMessage().fromJson(Published.serializer())

        coVerifySequence { mockAaregClient.hentArbeidsforhold(expected.identitetsnummer, expected.uuid.toString()) }
        testRapid.inspektør.size shouldBeExactly 2
        actual shouldBe expected
    }
})

@Serializable
@OptIn(ExperimentalSerializationApi::class)
private data class Published(
    @JsonNames("@løsning")
    val loesning: Map<BehovType, ArbeidsforholdLøsning>,
    val identitetsnummer: String,
    val uuid: UUID
) {
    @EncodeDefault
    @JsonNames("@event_name")
    val eventName = EventName.INSENDING_STARTED

    @EncodeDefault
    @JsonNames("@behov")
    val behov = BehovType.ARBEIDSFORHOLD

    companion object {
        fun mockSuccess(): Published =
            Published(
                loesning = mapOf(
                    BehovType.ARBEIDSFORHOLD to mockLøsningSuccess()
                ),
                identitetsnummer = "collide-levitator-modify",
                uuid = UUID.randomUUID()
            )

        fun mockFailure(): Published =
            Published(
                loesning = mapOf(
                    BehovType.ARBEIDSFORHOLD to mockLøsningFailure()
                ),
                identitetsnummer = "oil-probably-stack",
                uuid = UUID.randomUUID()
            )
    }
}
