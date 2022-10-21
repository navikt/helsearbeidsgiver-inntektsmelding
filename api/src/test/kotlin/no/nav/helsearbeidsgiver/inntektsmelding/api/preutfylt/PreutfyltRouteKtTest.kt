package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
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
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.buildObjectMapper
import no.nav.helsearbeidsgiver.inntektsmelding.api.buildResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.InnsendingFeilet
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

internal class PreutfyltRouteKtTest {

    private val producer = mockk<PreutfyltProducer>()
    private val poller = mockk<RedisPoller>()

    private val UUID = "abc-123"
    private val GYLDIG_REQUEST = PreutfyltRequest(TestData.validOrgNr, TestData.validIdentitetsnummer)
    private val UGYLDIG_REQUEST = PreutfyltRequest(TestData.notValidOrgNr, TestData.notValidIdentitetsnummer)
    private val RESULTAT_OK = buildResultat()

    @Test
    fun `skal godta og returnere kvittering`() = testApplication {
        every {
            producer.publish(any())
        } returns UUID
        every {
            runBlocking {
                poller.getResultat(any(), any(), any())
            }
        } returns RESULTAT_OK
        application {
            install(ContentNegotiation) {
                jackson {
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    registerModule(JavaTimeModule())
                }
            }
            routing {
                preutfyltRoute(producer, poller)
            }
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        val response = client.post("/preutfyll") {
            contentType(ContentType.Application.Json)
            setBody(GYLDIG_REQUEST)
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `skal håndtere bad request`() = testApplication {
        every {
            producer.publish(any())
        } returns UUID
        every {
            runBlocking {
                poller.getResultat(any(), any(), any())
            }
        } returns RESULTAT_OK
        application {
            install(ContentNegotiation) {
                jackson {
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    registerModule(JavaTimeModule())
                }
            }
            routing {
                preutfyltRoute(producer, poller)
            }
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        val response = client.post("/preutfyll") {
            contentType(ContentType.Application.Json)
            setBody(UGYLDIG_REQUEST)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNotNull(response.bodyAsText())
        val data: String = response.bodyAsText()
        val violations = buildObjectMapper().readValue<ValidationResponse>(data).errors
        assertEquals(2, violations.size)
    }

    @Test
    fun `skal håndtere server error`() = testApplication {
        every {
            producer.publish(any())
        } returns UUID
        every {
            runBlocking {
                poller.getResultat(any(), any(), any())
            }
        } throws RedisPollerTimeoutException(UUID)
        application {
            install(ContentNegotiation) {
                jackson {
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    registerModule(JavaTimeModule())
                }
            }
            routing {
                preutfyltRoute(producer, poller)
            }
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        val response = client.post("/preutfyll") {
            contentType(ContentType.Application.Json)
            setBody(GYLDIG_REQUEST)
        }
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(buildObjectMapper().writeValueAsString(InnsendingFeilet(UUID, "Brukte for lang tid")), response.bodyAsText())
    }
}
