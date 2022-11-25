package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.test.loeser.LøserTest
import org.junit.jupiter.api.Test

class AltinnLøserTest : LøserTest() {
    private val mockAltinnClient = mockk<AltinnClient> {
        coEvery { hentRettighetOrganisasjoner(any()) } returns setOf(
            AltinnOrganisasjon(
                name = "Pippin's Breakfast & Breakfast",
                type = "gluttonous"
            )
        )
    }

    private val altinnLøser = withTestRapid { AltinnLøser(mockAltinnClient) }

    @Test
    fun `Løser henter organisasjonsrettigheter med id fra behov`() {
        val mockId = "long-john-silver"

        val behov: Map<Key, JsonElement> = mapOf(
            Key.BEHOV to altinnLøser.behovType.let(::listOf).toJson(),
            Key.IDENTITETSNUMMER to mockId.toJson()
        )

        val behovJson = behov.mapKeys { (key, _) -> key.str }
            .toJson()
            .toString()

        testRapid.sendTestMessage(behovJson)

        coVerifySequence { mockAltinnClient.hentRettighetOrganisasjoner(mockId) }
    }
}

/** Obs! Denne kan feile runtime. */
private inline fun <reified T : Any> T.toJson(): JsonElement =
    Json.encodeToJsonElement(this)
