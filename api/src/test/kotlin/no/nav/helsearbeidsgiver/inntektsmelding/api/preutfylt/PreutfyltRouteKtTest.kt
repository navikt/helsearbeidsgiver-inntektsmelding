@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.RouteTester
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.buildResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.InnsendingFeilet
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

internal class PreutfyltRouteKtTest {
    private val objectMapper = customObjectMapper()

    private val poller = mockk<RedisPoller>()

    private val GYLDIG_REQUEST = PreutfyltRequest(TestData.validOrgNr, TestData.validIdentitetsnummer)
    private val UGYLDIG_REQUEST = PreutfyltRequest(TestData.notValidOrgNr, TestData.notValidIdentitetsnummer)
    private val RESULTAT_OK = buildResultat()

    @Test
    fun `skal godta og returnere kvittering`() = testApplication {
        coEvery {
            poller.getResultat(any(), any(), any())
        } returns RESULTAT_OK

        val response = routeTester().post(GYLDIG_REQUEST)

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `skal håndtere bad request`() = testApplication {
        coEvery {
            poller.getResultat(any(), any(), any())
        } returns RESULTAT_OK

        val response = routeTester().post(UGYLDIG_REQUEST)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNotNull(response.bodyAsText())
        val data: String = response.bodyAsText()
        val violations = objectMapper.readValue<ValidationResponse>(data).errors
        assertEquals(2, violations.size)
    }

    @Test
    fun `skal håndtere server error`() = testApplication {
        val routeTester = routeTester()

        coEvery {
            poller.getResultat(any(), any(), any())
        } throws RedisPollerTimeoutException(routeTester.mockUuid.toString())

        val response = routeTester.post(GYLDIG_REQUEST)

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(objectMapper.writeValueAsString(InnsendingFeilet(routeTester.mockUuid.toString(), "Brukte for lang tid")), response.bodyAsText())
    }

    private fun ApplicationTestBuilder.routeTester(): RouteTester =
        RouteTester(
            this,
            poller,
            "/preutfyll",
            RouteExtra::PreutfyltRoute
        )
}
