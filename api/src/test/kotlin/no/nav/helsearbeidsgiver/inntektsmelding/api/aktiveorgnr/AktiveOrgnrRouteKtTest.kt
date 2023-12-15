@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class AktiveOrgnrRouteKtTest : ApiTest() {
    private val path = Routes.PREFIX + Routes.AKTIVEORGNR

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `skal godta og returnere liste med organisasjoner`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getString(any(), any(), any())
        } returns Mock.GYLDIG_AKTIVE_ORGNR_RESPONSE
        val requestBody = """
            {"identitetsnummer":"test-fnr"}
        """
        val response = post(path, requestBody.fromJson(AktiveOrgnrRequest.serializer()), AktiveOrgnrRequest.serializer())
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(Mock.GYLDIG_AKTIVE_ORGNR_RESPONSE, response.bodyAsText())
    }

    @Test
    fun `test request data`() {
        val requestBody = """
            {"identitetsnummer":"test-fnr"}
        """.removeJsonWhitespace()
        val requestObj = requestBody.fromJson(AktiveOrgnrRequest.serializer())
        assertEquals("test-fnr", requestObj.identitetsnummer)
    }

    private object Mock {
        val forespoerselId: UUID = UUID.randomUUID()

        val GYLDIG_AKTIVE_ORGNR_RESPONSE = """
            {
                "fulltNavn": "test-navn",
                "underenheter": [{"orgnrUnderenhet": "test-orgnr", "virksomhetsnavn": "test-orgnavn"}]
            }
        """.removeJsonWhitespace()
    }
}
