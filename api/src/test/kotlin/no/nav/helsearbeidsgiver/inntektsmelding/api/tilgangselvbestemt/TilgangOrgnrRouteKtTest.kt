package no.nav.helsearbeidsgiver.inntektsmelding.api.tilgangselvbestemt

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ikkeTilgangResultat
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

private val pathMedGyldigOrgnr =
    Routes.PREFIX +
        Routes.TILGANG_ORGNR.replaceFirst("{orgnr}", Orgnr.genererGyldig().toString())

private val pathMedUgyldigOrgnr =
    Routes.PREFIX +
        Routes.TILGANG_ORGNR.replaceFirst("{orgnr}", "heipådeg")

private val pathUtenId =
    Routes.PREFIX +
        Routes.TILGANG_ORGNR.replaceFirst("{orgnr}", "")

class TilgangOrgnrRouteKtTest : ApiTest() {
    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `gyldig orgnr og tilgang skal gi 200 OK`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } returns harTilgangResultat

            val response = get(pathMedGyldigOrgnr)

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.OK
            actualJson shouldBe ""
        }

    @Test
    fun `gyldig orgnr og manglende tilgang skal gi 403 Forbidden`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } returns ikkeTilgangResultat

            val response = get(pathMedGyldigOrgnr)

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.Forbidden
            actualJson shouldBe "Du har ikke rettigheter for organisasjon."
        }

    @Test
    fun `ugyldig orgnr skal gi bad request`() =
        testApi {
            val response = get(pathMedUgyldigOrgnr)

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.BadRequest
            actualJson shouldBe "Ugyldig orgnr"
        }

    @Test
    fun `manglende orgnr skal gi 404 Not found`() =
        testApi {
            val response = get(pathUtenId)
            response.status shouldBe HttpStatusCode.NotFound
        }

    @Test
    fun `timeout mot redis gir 500-feil`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } throws RedisPollerTimeoutException(UUID.randomUUID())

            val response = get(pathMedGyldigOrgnr)

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe "Teknisk feil"
        }

    @Test
    fun `ukjent feil gir 500-feil`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } throws NullPointerException()

            val response = get(pathMedGyldigOrgnr)

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe "Teknisk feil"
        }
}
