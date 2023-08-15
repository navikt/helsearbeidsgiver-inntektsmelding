@file:UseSerializers(UuidSerializer::class)

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
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.json.toJson
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.ArbeidsforholdLøser
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.tilArbeidsforhold
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
        val expected = PublishedData.mock()

        coEvery { mockAaregClient.hentArbeidsforhold(any(), any()) } returns mockKlientArbeidsforhold().let(::listOf)

        testRapid.sendJson(
            Key.EVENT_NAME to expected.eventName.toJson(),
            Key.BEHOV to BehovType.ARBEIDSFORHOLD.toJson(),
            Key.IDENTITETSNUMMER to Mock.FNR.toJson(),
            Key.UUID to expected.uuid.toJson()
        )

        val actual = testRapid.firstMessage().fromJson(PublishedData.serializer())

        coVerifySequence { mockAaregClient.hentArbeidsforhold(Mock.FNR, expected.uuid.toString()) }
        testRapid.inspektør.size shouldBeExactly 1
        actual shouldBe expected
    }

    test("ved feil fra aareg så publiseres løsning med feilmelding") {
        val expected = PublishedFeil.mock()

        coEvery { mockAaregClient.hentArbeidsforhold(any(), any()) } throws RuntimeException()

        testRapid.sendJson(
            Key.EVENT_NAME to expected.eventName.toJson(),
            Key.BEHOV to BehovType.ARBEIDSFORHOLD.toJson(),
            Key.IDENTITETSNUMMER to Mock.FNR.toJson(),
            Key.UUID to expected.uuid.toJson()
        )

        val actual = testRapid.firstMessage().fromJson(PublishedFeil.serializer())

        coVerifySequence { mockAaregClient.hentArbeidsforhold(Mock.FNR, expected.uuid.toString()) }
        testRapid.inspektør.size shouldBeExactly 1
        actual shouldBe expected
    }
})

private object Mock {
    const val FNR = "12121200012"
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
private data class PublishedData(
    val uuid: UUID,
    val arbeidsforhold: Data
) {
    @JsonNames("@event_name")
    val eventName = EventName.INSENDING_STARTED
    val data = ""

    companion object {
        fun mock(): PublishedData =
            PublishedData(
                uuid = UUID.randomUUID(),
                arbeidsforhold = mockKlientArbeidsforhold().tilArbeidsforhold().let(::listOf).let(::Data)
            )
    }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
private data class PublishedFeil(
    val uuid: UUID,
    val fail: JsonElement
) {
    @JsonNames("@event_name")
    val eventName = EventName.INSENDING_STARTED

    companion object {
        fun mock(): PublishedFeil {
            val uuid = UUID.randomUUID()

            return PublishedFeil(
                uuid = uuid,
                fail = mapOf(
                    Fail::behov.name to BehovType.ARBEIDSFORHOLD.toJson(),
                    Fail::feilmelding.name to "Klarte ikke hente arbeidsforhold".toJson(),
                    Fail::data.name to JsonNull,
                    Fail::uuid.name to uuid.toJson(),
                    Fail::forespørselId.name to JsonNull
                ).toJson()
            )
        }
    }
}

@Serializable
private data class Data(
    val t: List<Arbeidsforhold>
)
