package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.TestClient
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.identitetsnummer
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
class AuthorizationTest : ApiTest() {

    @Test
    fun `stopp uautoriserte kall mot API`() = testApi {
        listOf(
            Routes.TRENGER to ::postUtenAuth
            // Auth midlertidig deaktivert for enklere manuell testing
            // Routes.INNSENDING to ::postUtenAuth
        ).forEach { (path, callFn) ->
            val response = callFn(Routes.PREFIX + path)

            Assertions.assertEquals(
                HttpStatusCode.Unauthorized,
                response.status,
                "Test feiler mot '$path'."
            )
        }
    }

    @Test
    fun `identitetsnummer kan leses fra autorisasjonstoken`() = testApplication {
        val path = "/test/auth"

        application {
            apiModule(mockk(relaxed = true))

            routing {
                authenticate {
                    get(path) {
                        val identitetsnummer = try {
                            identitetsnummer()
                        } catch (e: Exception) {
                            Assertions.fail("Kunne ikke lese identitetsnummer pga. exception.", e)
                        }

                        Assertions.assertEquals(mockPid, identitetsnummer)

                        respondOk("", String.serializer())
                    }
                }
            }
        }

        val testClient = TestClient(this) { mockAuthToken() }

        val response = testClient.get(path)

        Assertions.assertEquals(HttpStatusCode.OK, response.status)
    }
}

private fun TestClient.getUtenAuth(path: String): HttpResponse =
    get(
        path = path,
        block = {} // override default auth-block
    )

private fun TestClient.postUtenAuth(path: String): HttpResponse =
    post(
        path = path,
        body = "",
        bodySerializer = String.serializer(),
        block = {} // override default auth-block
    )
