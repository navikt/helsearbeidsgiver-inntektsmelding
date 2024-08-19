package no.nav.helsearbeidsgiver.inntektsmelding.api.inntektselvbestemt

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
}
