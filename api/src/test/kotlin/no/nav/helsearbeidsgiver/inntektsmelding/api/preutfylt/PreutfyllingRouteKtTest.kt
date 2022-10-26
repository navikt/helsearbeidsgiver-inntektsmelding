package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RouteTester
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.buildResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PreutfyllingRouteKtTest {
    private val poller = mockk<RedisPoller>()

    private val GYLDIG_REQUEST = PreutfyllRequest(TestData.validOrgNr, TestData.validIdentitetsnummer)
    private val RESULTAT_OK = buildResultat()

    @Test
    fun `skal godta og returnere kvittering`() = testApplication {
        coEvery {
            poller.getResultat(any(), any(), any())
        } returns RESULTAT_OK

        val routeTester = RouteTester(
            this,
            poller,
            "/preutfyll",
            RouteExtra::preutfyltRoute
        )

        val response = routeTester.post(GYLDIG_REQUEST)

        assertEquals(HttpStatusCode.Created, response.status)
        // assertEquals(objectMapper.writeValueAsString(PreutfyltResponse(UUID)), response.bodyAsText())
    }
}
