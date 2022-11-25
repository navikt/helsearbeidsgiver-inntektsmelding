package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
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

    private val testRapid = mockRapid { AltinnLøser(mockAltinnClient) }

    @Test
    fun `Løser henter organisasjonsrettigheter med id fra behov`() {
        val id = "long-john-silver"

        val behov: Map<Key, JsonElement> = mapOf(
            Key.BEHOV to listOf(BehovType.ARBEIDSGIVERE).toJson(),
            Key.IDENTITETSNUMMER to id.toJson()
        )

        val behovJson = behov.mapKeys { (key, _) -> key.str }
            .toJson()
            .toString()

        testRapid.sendTestMessage(behovJson)

        coVerifySequence { mockAltinnClient.hentRettighetOrganisasjoner(id) }
    }
}

/** Obs! Denne kan feile runtime. */
private inline fun <reified T : Any> T.toJson(): JsonElement =
    Json.encodeToJsonElement(this)
