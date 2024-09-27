package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.lesFnrFraAuthToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.TestClient
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class AuthorizationTest : ApiTest() {
    @Test
    fun `stopp uautoriserte kall mot API`() =
        testApi {
            listOf(
                Routes.HENT_FORESPOERSEL to ::postUtenAuth,
                Routes.INNTEKT to ::postUtenAuth,
                Routes.INNTEKT_SELVBESTEMT to ::postUtenAuth,
                Routes.INNSENDING + "/0" to ::postUtenAuth,
                Routes.SELVBESTEMT_INNTEKTSMELDING to ::postUtenAuth,
                Routes.SELVBESTEMT_INNTEKTSMELDING + "/0" to ::getUtenAuth,
                Routes.KVITTERING to ::getUtenAuth,
                Routes.AKTIVEORGNR to ::postUtenAuth,
                Routes.TILGANG_ORGNR to ::getUtenAuth,
            ).forEach { (path, callFn) ->
                val response = callFn(Routes.PREFIX + path)

                Assertions.assertEquals(
                    HttpStatusCode.Unauthorized,
                    response.status,
                    "Test feiler mot '$path'.",
                )
            }
        }

    @Test
    fun `fnr kan leses fra autorisasjonstoken`() =
        testApplication {
            val path = "/test/auth"

            application {
                apiModule(mockk(relaxed = true), mockk())

                routing {
                    authenticate {
                        get(path) {
                            val fnr =
                                try {
                                    call.request.lesFnrFraAuthToken()
                                } catch (e: Exception) {
                                    Assertions.fail("Klarte ikke lese fnr pga. exception.", e)
                                }

                            Assertions.assertEquals(mockPid, fnr)

                            respondOk("", String.serializer())
                        }
                    }
                }
            }

            val testClient = TestClient(this, ::mockAuthToken)

            val response = testClient.get(path)

            Assertions.assertEquals(HttpStatusCode.OK, response.status)
        }
}

private fun TestClient.getUtenAuth(path: String): HttpResponse =
    get(
        path = path,
        block = {}, // override default auth-block
    )

private fun TestClient.postUtenAuth(path: String): HttpResponse =
    post(
        path = path,
        body = "",
        bodySerializer = String.serializer(),
        block = {}, // override default auth-block
    )
