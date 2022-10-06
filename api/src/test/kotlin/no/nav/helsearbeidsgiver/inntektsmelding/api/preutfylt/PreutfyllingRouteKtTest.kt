package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.buildResultat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PreutfyllingRouteKtTest {

    private val producer = mockk<PreutfyltProducer>()
    private val poller = mockk<RedisPoller>()

    private val UUID = "abc-123"
    private val GYLDIG_REQUEST = PreutfyllRequest(TestData.validOrgNr, TestData.validIdentitetsnummer)
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
        // assertEquals(objectMapper.writeValueAsString(PreutfyltResponse(UUID)), response.bodyAsText())
    }
}
