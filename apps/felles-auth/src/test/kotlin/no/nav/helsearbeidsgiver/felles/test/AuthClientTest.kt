package no.nav.helsearbeidsgiver.felles.test

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import no.nav.helsearbeidsgiver.felles.auth.AuthClient
import no.nav.helsearbeidsgiver.felles.auth.IdentityProvider
import no.nav.helsearbeidsgiver.felles.auth.TokenIntrospectionResponse
import no.nav.helsearbeidsgiver.felles.auth.TokenResponse
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace

class AuthClientTest :
    FunSpec({

        context(AuthClient::tokenGetter.name) {
            test("Lager funksjon som henter access token") {
                val authClient = mockAuthClient(Mock.tokenJson)

                val tokenGetter = authClient.tokenGetter(IdentityProvider.AZURE_AD, "mock target")

                tokenGetter() shouldBe "there is no spoon"
            }
        }

        context(AuthClient::token.name) {
            test("Henter access token") {
                val authClient = mockAuthClient(Mock.tokenJson)

                val token = authClient.token(IdentityProvider.AZURE_AD, "mock target")

                token shouldBe
                    TokenResponse(
                        accessToken = "there is no spoon",
                        expiresInSeconds = -666,
                    )
            }

            test("token - Kaster egendefinert exception ved responsfeil") {
                val authClient = mockAuthClient(Mock.errorResponseJson, HttpStatusCode.NotAcceptable)

                val e =
                    shouldThrowExactly<RuntimeException> {
                        authClient.token(IdentityProvider.AZURE_AD, "mock target")
                    }

                e.message shouldBe "Klarte ikke hente token. Feilet med status '406 Not Acceptable' og feilmelding 'blue pill doink'."
            }

            test("token - Kaster exception ved ukjent feil") {
                val authClient = mockAuthClient("lykke til med denne responsen!")

                shouldThrowExactly<JsonConvertException> {
                    authClient.token(IdentityProvider.AZURE_AD, "mock target")
                }
            }
        }

        context(AuthClient::exchange.name) {
            test("Exchanger access token") {
                val authClient = mockAuthClient(Mock.tokenJson)

                val token = authClient.exchange(IdentityProvider.TOKEN_X, "mock target", "mock user token")

                token shouldBe
                    TokenResponse(
                        accessToken = "there is no spoon",
                        expiresInSeconds = -666,
                    )
            }

            test("exchange - Kaster egendefinert exception ved responsfeil") {
                val authClient = mockAuthClient(Mock.errorResponseJson, HttpStatusCode.ExpectationFailed)

                val e =
                    shouldThrowExactly<RuntimeException> {
                        authClient.exchange(IdentityProvider.AZURE_AD, "mock target", "mock user token")
                    }

                e.message shouldBe "Klarte ikke hente token. Feilet med status '417 Expectation Failed' og feilmelding 'blue pill doink'."
            }

            test("exchange - Kaster exception ved ukjent feil") {
                val authClient = mockAuthClient("""{"gyldigJson":"men feil innhold"}""")

                shouldThrowExactly<JsonConvertException> {
                    authClient.exchange(IdentityProvider.AZURE_AD, "mock target", "mock user token")
                }
            }
        }

        context(AuthClient::introspect.name) {
            test("Validerer OK for gyldig access token") {
                val authClient = mockAuthClient(Mock.tokenIntrospectionSuccessJson)

                val introspection = authClient.introspect(IdentityProvider.AZURE_AD, "mock access token")

                introspection shouldBe
                    TokenIntrospectionResponse(
                        active = true,
                        error = null,
                    )
            }

            test("Validerer _ikke_ OK for ugyldig access token") {
                val authClient = mockAuthClient(Mock.tokenIntrospectionFailureJson)

                val introspection = authClient.introspect(IdentityProvider.AZURE_AD, "mock access token")

                introspection shouldBe
                    TokenIntrospectionResponse(
                        active = false,
                        error = "i need guns, lots of guns",
                    )
            }

            test("introspect - Kaster exception ved ukjent feil") {
                val authClient = mockAuthClient(Mock.tokenIntrospectionFailureJson, HttpStatusCode.ExpectationFailed)

                shouldThrow<ResponseException> {
                    authClient.introspect(IdentityProvider.AZURE_AD, "mock access token")
                }
            }
        }
    })

object Mock {
    val tokenJson =
        """
        {
            "access_token": "there is no spoon",
            "expires_in": -666
        }
        """.removeJsonWhitespace()

    val tokenIntrospectionSuccessJson =
        """
        {
            "active": true
        }
        """.removeJsonWhitespace()

    val tokenIntrospectionFailureJson =
        """
        {
            "active": false,
            "error": "i need guns, lots of guns"
        }
        """.removeJsonWhitespace()

    val errorResponseJson =
        """
        {
            "error": "blue pill or red pill?",
            "error_description": "blue pill doink"
        }
        """.removeJsonWhitespace()
}
