package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson

class AltinnLoeserTest : FunSpec({
    val testRapid = TestRapid()

    val mockAltinnClient = mockk<AltinnClient>(relaxed = true)

    AltinnLoeser(mockAltinnClient).connect(testRapid)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
    }

    test("henter organisasjonsrettigheter med id fra behov") {
        coEvery { mockAltinnClient.hentRettighetOrganisasjoner(any()) } returns mockAltinnOrganisasjonSet()

        val mockId = "long-john-silver"
        val mockUuid = randomUuid()

        val expectedPublished = mapOf<IKey, JsonElement>(
            Key.DATA to "".toJson(),
            Key.UUID to mockUuid.toJson(),
            Key.ORG_RETTIGHETER to mockAltinnOrganisasjonSet().mapNotNull { it.orgnr }.toSet().toJson(String.serializer().set())
        )

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
            Key.UUID to mockUuid.toJson(),
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
            Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
            Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
            Key.IDENTITETSNUMMER to mockId.toJson()
        )

        testRapid.inspektør.size shouldBe 0

        coVerify(exactly = 0) { mockAltinnClient.hentRettighetOrganisasjoner(any()) }
    }

    test("ufullstendig melding aktiverer ikke river") {
        testRapid.sendJson(
            Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
            Key.BEHOV to BehovType.ARBEIDSGIVERE.toJson()
        )

        testRapid.inspektør.size shouldBe 0

        coVerify(exactly = 0) { mockAltinnClient.hentRettighetOrganisasjoner(any()) }
    }
})

private fun mockAltinnOrganisasjonSet(): Set<AltinnOrganisasjon> =
    setOf(
        AltinnOrganisasjon(
            navn = "Pippin's Breakfast & Breakfast",
            type = "gluttonous"
        )
    )
