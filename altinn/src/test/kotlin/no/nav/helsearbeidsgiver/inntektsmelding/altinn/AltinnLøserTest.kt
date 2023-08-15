package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.mockk
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.loeser.toLøsningSuccess
import no.nav.helsearbeidsgiver.felles.test.loeser.LøserTest
import no.nav.helsearbeidsgiver.felles.test.loeser.LøserTest.LøserAnswer.Companion.toLøserAnswer
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Test
import java.util.UUID

class AltinnLøserTest : LøserTest() {
    private val mockAltinnClient = mockk<AltinnClient> {
        coEvery { hentRettighetOrganisasjoner(any()) } returns mockAltinnOrganisasjonSet()
    }

    private val altinnLøser = withTestRapid { AltinnLøser(mockAltinnClient) }

    @Test
    fun `Løser henter organisasjonsrettigheter med id fra behov`() {
        val mockId = "long-john-silver"

        val expectedAnswer = LøserAnswer(
            behovType = altinnLøser.behovType,
            initiateId = UUID.randomUUID(),
            løsning = mockAltinnOrganisasjonSet().toLøsningSuccess()
        )

        testRapid.sendJson(
            Key.BEHOV to expectedAnswer.behovType.let(::listOf).toJson(BehovType.serializer()),
            Key.INITIATE_ID to expectedAnswer.initiateId.toJson(),
            Key.IDENTITETSNUMMER to mockId.toJson()
        )

        val actualAnswer = testRapid.firstMessage()
            .toLøserAnswer(AltinnOrganisasjon.serializer().set())

        coVerifySequence { mockAltinnClient.hentRettighetOrganisasjoner(mockId) }
        actualAnswer shouldBe expectedAnswer
    }
}

private fun mockAltinnOrganisasjonSet(): Set<AltinnOrganisasjon> =
    setOf(
        AltinnOrganisasjon(
            name = "Pippin's Breakfast & Breakfast",
            type = "gluttonous"
        )
    )
