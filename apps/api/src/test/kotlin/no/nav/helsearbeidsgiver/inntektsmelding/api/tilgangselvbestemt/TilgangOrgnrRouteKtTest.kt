package no.nav.helsearbeidsgiver.inntektsmelding.api.tilgangselvbestemt

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ikkeTilgangResultat
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private val pathMedGyldigOrgnr =
    Routes.PREFIX +
        Routes.TILGANG_ORGNR.replaceFirst("{${Routes.Params.orgnr.key}}", Orgnr.genererGyldig().toString())

private val pathMedUgyldigOrgnr =
    Routes.PREFIX +
        Routes.TILGANG_ORGNR.replaceFirst("{${Routes.Params.orgnr.key}}", "heipådeg")

private val pathUtenId =
    Routes.PREFIX +
        Routes.TILGANG_ORGNR.replaceFirst("{${Routes.Params.orgnr.key}}", "")

class TilgangOrgnrRouteKtTest : ApiTest() {
    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `gyldig orgnr og tilgang skal gi 200 OK`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returns harTilgangResultat

            val response = get(pathMedGyldigOrgnr)

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText().shouldBeEmpty()
        }

    @Test
    fun `gyldig orgnr og manglende tilgang skal gi 403 Forbidden`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returns ikkeTilgangResultat

            val response = get(pathMedGyldigOrgnr)
            val responseBody = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.Forbidden
            responseBody.shouldBeTypeOf<ErrorResponse.ManglerTilgang>()
        }

    @Test
    fun `ugyldig orgnr skal gi bad request`() =
        testApi {
            val response = get(pathMedUgyldigOrgnr)
            val responseBody = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.BadRequest
            responseBody.shouldBeTypeOf<ErrorResponse.InvalidPathParameter>()
            responseBody.parameterKey shouldBe Routes.Params.orgnr.key
            responseBody.parameterValue shouldBe "heipådeg"
        }

    @Test
    fun `manglende orgnr skal gi 404 Not found`() =
        testApi {
            val response = get(pathUtenId)

            response.status shouldBe HttpStatusCode.NotFound
            response.bodyAsText().shouldBeEmpty()
        }

    @Test
    fun `timeout mot redis gir 500-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returns null

            val response = get(pathMedGyldigOrgnr)
            val responseBody = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.InternalServerError
            responseBody.shouldBeTypeOf<ErrorResponse.RedisTimeout>()
        }

    @Test
    fun `ukjent feil gir 500-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } throws NullPointerException()

            val response = get(pathMedGyldigOrgnr)

            val error = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.InternalServerError
            error.shouldBeTypeOf<ErrorResponse.Unknown>()
        }
}
