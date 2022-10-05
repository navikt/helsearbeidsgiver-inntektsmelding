@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.DummyConstraintViolation
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.buildObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

internal class InnsendingRouteKtTest {

    val producer = mockk<InnsendingProducer>()
    val poller = mockk<RedisPoller>()
    val objectMapper = buildObjectMapper()
    val GYLDIG_REQUEST = InntektsmeldingRequest(TestData.validOrgNr, TestData.validIdentitetsnummer)
    val UGYLDIG_REQUEST = InntektsmeldingRequest(TestData.notValidOrgNr, TestData.notValidIdentitetsnummer)

    val UUID = "abc-123"
    val RESULTAT_OK = Resultat(FULLT_NAVN = NavnLøsning("verdi"))
    val RESULTAT_FEIL = Resultat(FULLT_NAVN = NavnLøsning(error = Feilmelding("feil", 500)))

    @Test
    fun `skal godta og returnere kvittering`() = testApplication {
        every {
            producer.publish(any())
        } returns UUID
        every {
            runBlocking {
                poller.getValue(any(), any(), any())
            }
        } returns objectMapper.writeValueAsString(RESULTAT_OK)
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        application {
            install(ContentNegotiation) {
                jackson()
            }
            routing {
                innsendingRoute(producer, poller, objectMapper)
            }
        }
        val response = client.post("/inntektsmelding") {
            contentType(ContentType.Application.Json)
            setBody(GYLDIG_REQUEST)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(objectMapper.writeValueAsString(InnsendingResponse(UUID)), response.bodyAsText())
    }

    @Test
    fun `skal returnere valideringsfeil ved ugyldig request`() = testApplication {
        every {
            producer.publish(any())
        } returns UUID
        every {
            runBlocking {
                poller.getValue(any(), any(), any())
            }
        } returns objectMapper.writeValueAsString(RESULTAT_FEIL)
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        application {
            install(ContentNegotiation) {
                jackson()
            }
            routing {
                innsendingRoute(producer, poller, objectMapper)
            }
        }
        val response = client.post("/inntektsmelding") {
            contentType(ContentType.Application.Json)
            setBody(UGYLDIG_REQUEST)
        }
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
        every {
            producer.publish(any())
        } returns UUID
        every {
            runBlocking {
                poller.getValue(any(), any(), any())
            }
        } throws RedisPollerTimeoutException(UUID)
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        application {
            install(ContentNegotiation) {
                jackson()
            }
            routing {
                innsendingRoute(producer, poller, objectMapper)
            }
        }
        val response = client.post("/inntektsmelding") {
            contentType(ContentType.Application.Json)
            setBody(GYLDIG_REQUEST)
        }
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(objectMapper.writeValueAsString(InnsendingFeilet(UUID, "Brukte for lang tid")), response.bodyAsText())
    }

    @Test
    fun `skal vise feil når et behov feiler`() = testApplication {
        every {
            producer.publish(any())
        } returns UUID
        every {
            runBlocking {
                poller.getValue(any(), any(), any())
            }
        } returns objectMapper.writeValueAsString(RESULTAT_FEIL)
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        application {
            install(ContentNegotiation) {
                jackson()
            }
            routing {
                innsendingRoute(producer, poller, objectMapper)
            }
        }
        val response = client.post("/inntektsmelding") {
            contentType(ContentType.Application.Json)
            setBody(UGYLDIG_REQUEST)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNotNull(response.bodyAsText())
        val data: String = response.bodyAsText()
        val violations = objectMapper.readValue<List<DummyConstraintViolation>>(data)
        assertEquals(2, violations.size)
    }
}
