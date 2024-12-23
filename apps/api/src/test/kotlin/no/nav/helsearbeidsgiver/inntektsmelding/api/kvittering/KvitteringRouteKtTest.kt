package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.felles.domene.KvitteringResultat
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.mock.mockEksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

private const val PATH = Routes.PREFIX + Routes.KVITTERING

class KvitteringRouteKtTest : ApiTest() {
    @Test
    fun `skal ikke godta tom uuid`() =
        testApi {
            val response = get("$PATH?uuid=")
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `skal ikke godta ugyldig uuid`() =
        testApi {
            val response = get("$PATH?uuid=ikke-en-uuid")
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `skal godta gyldig uuid`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    ResultJson(
                        success = resultat.toJson(KvitteringResultat.serializer()),
                    ).toJson()
                        .toString(),
                )

            val response = get(PATH + "?uuid=" + UUID.randomUUID())

            assertEquals(HttpStatusCode.OK, response.status)
        }
}

private val resultat =
    KvitteringResultat(
        forespoersel = mockForespoersel(),
        sykmeldtNavn = "Syk Meldt",
        avsenderNavn = "Avs Ender",
        orgNavn = "Orga Nisasjon",
        skjema = mockSkjemaInntektsmelding(),
        inntektsmelding = mockInntektsmelding(),
        eksternInntektsmelding = mockEksternInntektsmelding(),
    )
