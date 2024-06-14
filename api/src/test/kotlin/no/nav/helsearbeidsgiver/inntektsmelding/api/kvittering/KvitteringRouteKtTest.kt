package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

private const val PATH = Routes.PREFIX + Routes.KVITTERING

class KvitteringRouteKtTest : ApiTest() {

    @Test
    fun `skal ikke godta tom uuid`() = testApi {
        val response = get("$PATH?uuid=")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `skal ikke godta ugyldig uuid`() = testApi {
        val response = get("$PATH?uuid=ikke-en-uuid")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `skal godta gyldig uuid`() = testApi {
        coEvery { mockRedisPoller.hent(any()) } returnsMany listOf(
            harTilgangResultat,
            ResultJson(
                success = resultatMedInntektsmelding.parseJson()
            ).toJson(ResultJson.serializer())
        )

        val response = get(PATH + "?uuid=" + UUID.randomUUID())

        assertEquals(HttpStatusCode.OK, response.status)
    }
}

private val resultatMedInntektsmelding =
    """
    {
        "dokument": {
            "orgnrUnderenhet": "123456789",
            "identitetsnummer": "12345678901",
            "fulltNavn": "Ukjent",
            "virksomhetNavn": "Ukjent",
            "behandlingsdager": [],
            "egenmeldingsperioder": [],
            "bestemmendeFraværsdag": "2023-01-01",
            "fraværsperioder": [
            {
              "fom": "2023-01-01",
              "tom": "2023-01-31"
            }
            ],
            "arbeidsgiverperioder": [
            {
              "fom": "2023-01-01",
              "tom": "2023-01-16"
            }
            ],
            "beregnetInntekt": 3000,
            "fullLønnIArbeidsgiverPerioden": {
            "utbetalerFullLønn": true,
            "begrunnelse": null,
            "utbetalt": null
            },
            "refusjon": {
            "utbetalerHeleEllerDeler": false,
            "refusjonPrMnd": null,
            "refusjonOpphører": null,
            "refusjonEndringer": null
            },
            "naturalytelser": null,
            "tidspunkt": "2023-04-13T16:28:16.095083285+02:00",
            "årsakInnsending": "NY",
            "identitetsnummerInnsender": ""
        }
    }
    """.removeJsonWhitespace()
