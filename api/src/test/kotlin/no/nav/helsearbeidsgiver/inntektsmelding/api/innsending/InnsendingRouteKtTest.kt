package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

internal class InnsendingRouteKtTest {

    val producer = mockk<InnsendingProducer>()
    val poller = mockk<RedisPoller>()
    val GYLDIG_REQUEST = InntektsmeldingRequest("123456789", "12345678901")
    val UGYLDIG_REQUEST = InntektsmeldingRequest("", "")

    @Test
    fun skal_gi_feilmelding_ugyldig_request() = testApplication {
        every {
            producer.publish(any())
        } returns "Unit"
        every {
            runBlocking {
                poller.getValue(any(), any(), any())
            }
        } returns "uuid"
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
                innsendingRoute(producer, poller)
            }
        }
        val response = client.post("/inntektsmelding") {
            contentType(ContentType.Application.Json)
            setBody(UGYLDIG_REQUEST)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNotNull(response.bodyAsText())
    }

    @Test
    fun skal_gi_feilmelding_naar_redis_ikke_faar_data() = testApplication {
        every {
            producer.publish(any())
        } returns "Unit"
        every {
            runBlocking {
                poller.getValue(any(), any(), any())
            }
        } throws RedisPollerTimeoutException("uuid")
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
                innsendingRoute(producer, poller)
            }
        }
        val response = client.post("/inntektsmelding") {
            contentType(ContentType.Application.Json)
            setBody(GYLDIG_REQUEST)
        }
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun skal_sende_inn() = testApplication {
        every {
            producer.publish(any())
        } returns "Unit"
        every {
            runBlocking {
                poller.getValue(any(), any(), any())
            }
        } returns "abc"
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
                innsendingRoute(producer, poller)
            }
        }
        val response = client.post("/inntektsmelding") {
            contentType(ContentType.Application.Json)
            setBody(GYLDIG_REQUEST)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("abc", response.bodyAsText())
    }
}
