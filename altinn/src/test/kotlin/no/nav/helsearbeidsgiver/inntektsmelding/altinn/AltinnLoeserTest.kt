package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockObject

class AltinnLoeserTest : FunSpec({
    val testRapid = TestRapid()

    val mockAltinnClient = mockk<AltinnClient>(relaxed = true)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
    }

    mockObject(RapidApplication) {
        every { RapidApplication.create(any()) } returns testRapid

        AltinnLoeser(mockAltinnClient)
    }

    test("henter organisasjonsrettigheter med id fra behov") {
        coEvery { mockAltinnClient.hentRettighetOrganisasjoner(any()) } returns mockAltinnOrganisasjonSet()

        val mockId = "long-john-silver"

        val expectedPublished = mapOf<IKey, JsonElement>(
            Key.BEHOV to BehovType.ARBEIDSGIVERE.toJson(),
            Key.DATA to "".toJson(),
            DataFelt.ARBEIDSFORHOLD to mockAltinnOrganisasjonSet().toJson(AltinnOrganisasjon.serializer().set())
        )

        testRapid.sendJson(
            Key.BEHOV to BehovType.ARBEIDSGIVERE.toJson(),
            Key.IDENTITETSNUMMER to mockId.toJson()
        )

        val actualPublished = testRapid.firstMessage().toMap()

        coVerifySequence { mockAltinnClient.hentRettighetOrganisasjoner(mockId) }
        actualPublished shouldContainAll expectedPublished
    }

    test("melding med feil behov aktiverer ikke river") {
        val mockId = "small-jack-gold"

        testRapid.sendJson(
            Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
            Key.IDENTITETSNUMMER to mockId.toJson()
        )

        testRapid.inspektør.size shouldBe 0

        coVerify(exactly = 0) { mockAltinnClient.hentRettighetOrganisasjoner(any()) }
    }

    test("ufullstendig melding aktiverer ikke river") {
        testRapid.sendJson(
            Key.BEHOV to BehovType.ARBEIDSGIVERE.toJson()
        )

        testRapid.inspektør.size shouldBe 0

        coVerify(exactly = 0) { mockAltinnClient.hentRettighetOrganisasjoner(any()) }
    }
})

private fun mockAltinnOrganisasjonSet(): Set<AltinnOrganisasjon> =
    setOf(
        AltinnOrganisasjon(
            name = "Pippin's Breakfast & Breakfast",
            type = "gluttonous"
        )
    )
