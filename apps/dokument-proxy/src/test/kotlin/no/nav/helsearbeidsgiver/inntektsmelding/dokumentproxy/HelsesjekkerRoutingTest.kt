package no.nav.helsearbeidsgiver.inntektsmelding.dokumentproxy

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test

class HelsesjekkerRoutingTest {
    @Test
    fun `isalive endepunkt skal returnere I'm alive`() =
        testApplication {
            application {
                helsesjekkerRouting()
            }
            val response = client.get("isalive")
            response.bodyAsText() shouldBe "I'm alive"
            response.status shouldBe HttpStatusCode.OK
        }

    @Test
    fun `isready endepunkt skal returnere I'm ready`() =
        testApplication {
            application {
                helsesjekkerRouting()
            }
            val response = client.get("isready")
            response.bodyAsText() shouldBe "I'm ready"
            response.status shouldBe HttpStatusCode.OK
        }
}
