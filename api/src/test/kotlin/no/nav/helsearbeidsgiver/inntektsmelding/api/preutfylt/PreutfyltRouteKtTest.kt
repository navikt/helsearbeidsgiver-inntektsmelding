@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.buildResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.MockUuid
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

private const val PATH = Routes.PREFIX + Routes.PREUTFYLT

class PreutfyltRouteKtTest : ApiTest() {
    private val objectMapper = customObjectMapper()

    private val GYLDIG_REQUEST = PreutfyltRequest(TestData.validOrgNr, TestData.validIdentitetsnummer)
    private val UGYLDIG_REQUEST = PreutfyltRequest(TestData.notValidOrgNr, TestData.notValidIdentitetsnummer)
    private val RESULTAT_OK = buildResultat()

    @Test
    fun `skal godta og returnere kvittering`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns RESULTAT_OK

        val response = post(PATH, GYLDIG_REQUEST)

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `skal håndtere bad request`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns RESULTAT_OK

        val response = post(PATH, UGYLDIG_REQUEST)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNotNull(response.bodyAsText())
        val data: String = response.bodyAsText()
        val violations = objectMapper.readValue<ValidationResponse>(data).errors
        assertEquals(2, violations.size)
    }

    @Test
    fun `skal håndtere server error`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } throws RedisPollerTimeoutException(MockUuid.STRING)

        val response = post(PATH, GYLDIG_REQUEST)

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(objectMapper.writeValueAsString(RedisTimeoutResponse(MockUuid.STRING, "Brukte for lang tid")), response.bodyAsText())
    }
}
