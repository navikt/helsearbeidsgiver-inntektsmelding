package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HelsesjekkerRoutingKtTest {

    @Test
    fun is_alive_skal_virke() = testApplication {
        application {
            HelsesjekkerRouting()
        }
        val response = client.get("isalive")
        assertEquals("I'm alive", response.bodyAsText())
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun is_ready_skal_virke() = testApplication {
        application {
            HelsesjekkerRouting()
        }
        val response = client.get("isready")
        assertEquals("I'm ready", response.bodyAsText())
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
