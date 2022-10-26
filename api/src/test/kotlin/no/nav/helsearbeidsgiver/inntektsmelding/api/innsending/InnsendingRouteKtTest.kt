@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.inntektsmelding.api.DummyConstraintViolation
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.RouteTester
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class InnsendingRouteKtTest {
    val objectMapper = customObjectMapper()

    val poller = mockk<RedisPoller>()

    val GYLDIG_REQUEST = InntektsmeldingRequest(TestData.validOrgNr, TestData.validIdentitetsnummer)
    val UGYLDIG_REQUEST = InntektsmeldingRequest(TestData.notValidOrgNr, TestData.notValidIdentitetsnummer)

    val RESULTAT_OK = Resultat(FULLT_NAVN = NavnLøsning("verdi"))
    val RESULTAT_FEIL = Resultat(FULLT_NAVN = NavnLøsning(error = Feilmelding("feil", 500)))

    @Test
    fun `skal godta og returnere kvittering`() = testApplication {
        val routeTester = routeTester()

        coEvery {
            poller.getResultat(any(), any(), any())
        } returns RESULTAT_OK

        val response = routeTester.post(GYLDIG_REQUEST)

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(objectMapper.writeValueAsString(InnsendingResponse(routeTester.mockUuid.toString())), response.bodyAsText())
    }

    @Test
    fun `skal returnere valideringsfeil ved ugyldig request`() = testApplication {
        coEvery {
            poller.getResultat(any(), any(), any())
        } returns RESULTAT_FEIL

        val response = routeTester().post(UGYLDIG_REQUEST)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNotNull(response.bodyAsText())
        val data: String = response.bodyAsText()
        val violations = objectMapper.readValue<List<DummyConstraintViolation>>(data)
        assertEquals(2, violations.size)
        assertEquals("orgnrUnderenhet", violations[0].property)
        assertEquals("identitetsnummer", violations[1].property)
    }

    @Test
    fun `skal returnere feilmelding ved timeout fra Redis`() = testApplication {
        val routeTester = routeTester()

        coEvery {
            poller.getResultat(any(), any(), any())
        } throws RedisPollerTimeoutException(routeTester.mockUuid.toString())

        val response = routeTester.post(GYLDIG_REQUEST)

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(objectMapper.writeValueAsString(InnsendingFeilet(routeTester.mockUuid.toString(), "Brukte for lang tid")), response.bodyAsText())
    }

    @Test
    fun `skal vise feil når et behov feiler`() = testApplication {
        coEvery {
            poller.getResultat(any(), any(), any())
        } returns RESULTAT_FEIL

        val response = routeTester().post(UGYLDIG_REQUEST)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNotNull(response.bodyAsText())
        val data: String = response.bodyAsText()
        val violations = objectMapper.readValue<List<DummyConstraintViolation>>(data)
        assertEquals(2, violations.size)
    }

    private fun ApplicationTestBuilder.routeTester(): RouteTester =
        RouteTester(
            this,
            poller,
            "/inntektsmelding",
            RouteExtra::innsendingRoute
        )
}
