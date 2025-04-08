package no.nav.helsearbeidsgiver.felles.test

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import no.nav.helsearbeidsgiver.felles.auth.AuthClient
import no.nav.helsearbeidsgiver.felles.auth.ErrorResponse
import no.nav.helsearbeidsgiver.felles.auth.IdentityProvider
import no.nav.helsearbeidsgiver.felles.auth.TokenIntrospectionResponse
import no.nav.helsearbeidsgiver.felles.auth.TokenResponse
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace

class AuthClientTest :
    FunSpec({

        context(AuthClient::tokenGetter.name) {
            test("Lager funksjon som henter access token") {
                val authClient = mockAuthClient(Mock.tokenResponseJson)

                val tokenGetter = authClient.tokenGetter(IdentityProvider.AZURE_AD, "mock target")

                tokenGetter() shouldBe "there is no spoon"
            }
        }

        context(AuthClient::token.name) {
            test("Henter access token") {
                val authClient = mockAuthClient(Mock.tokenResponseJson)

                val token = authClient.token(IdentityProvider.AZURE_AD, "mock target")

                token shouldBe Mock.tokenResponse
            }

            test("token - Kaster ResponseException ved responsfeil") {
                val authClient = mockAuthClient(Mock.errorResponseJson, HttpStatusCode.NotAcceptable)

                val e =
                    shouldThrow<ResponseException> {
                        authClient.token(IdentityProvider.AZURE_AD, "mock target")
                    }

                e.response.status shouldBe HttpStatusCode.NotAcceptable
                e.response.body<ErrorResponse>() shouldBe Mock.errorResponse
            }

            test("token - Kaster exception ved ukjent feil") {
                val authClient = mockAuthClient(Mock.invalidJson)

                shouldThrowExactly<JsonConvertException> {
                    authClient.token(IdentityProvider.AZURE_AD, "mock target")
                }
            }
        }

        context(AuthClient::exchange.name) {
            test("Exchanger access token") {
                val authClient = mockAuthClient(Mock.tokenResponseJson)

                val token = authClient.exchange(IdentityProvider.TOKEN_X, "mock target", "mock user token")

                token shouldBe Mock.tokenResponse
            }

            test("exchange - Kaster ResponseException ved responsfeil") {
                val authClient = mockAuthClient(Mock.errorResponseJson, HttpStatusCode.ExpectationFailed)

                val e =
                    shouldThrow<ResponseException> {
                        authClient.exchange(IdentityProvider.AZURE_AD, "mock target", "mock user token")
                    }

                e.response.status shouldBe HttpStatusCode.ExpectationFailed
                e.response.body<ErrorResponse>() shouldBe Mock.errorResponse
            }

            test("exchange - Kaster exception ved ukjent feil") {
                val authClient = mockAuthClient(Mock.invalidJson)

                shouldThrowExactly<JsonConvertException> {
                    authClient.exchange(IdentityProvider.AZURE_AD, "mock target", "mock user token")
                }
            }
        }

        context(AuthClient::introspect.name) {
            test("Validerer OK for gyldig access token") {
                val authClient = mockAuthClient(Mock.tokenIntrospectionResponseSuccessJson)

                val introspection = authClient.introspect(IdentityProvider.AZURE_AD, "mock access token")

                introspection shouldBe
                    TokenIntrospectionResponse(
                        active = true,
                        error = null,
                    )
            }

            test("Validerer _ikke_ OK for ugyldig access token") {
                val authClient = mockAuthClient(Mock.tokenIntrospectionResponseFailureJson)

                val introspection = authClient.introspect(IdentityProvider.AZURE_AD, "mock access token")

                introspection shouldBe
                    TokenIntrospectionResponse(
                        active = false,
                        error = "i need guns, lots of guns",
                    )
            }

            test("introspect - Kaster ResponseException ved responsfeil") {
                val authClient = mockAuthClient("ikke viktig", HttpStatusCode.PaymentRequired)

                val e =
                    shouldThrow<ResponseException> {
                        authClient.introspect(IdentityProvider.AZURE_AD, "mock access token")
                    }

                e.response.status shouldBe HttpStatusCode.PaymentRequired
            }

            test("introspect - Kaster exception ved ukjent feil") {
                val authClient = mockAuthClient(Mock.invalidJson)

                shouldThrowExactly<JsonConvertException> {
                    authClient.introspect(IdentityProvider.AZURE_AD, "mock access token")
                }
            }
        }
    })

private object Mock {
    val tokenResponseJson =
        """
        {
            "access_token": "there is no spoon",
            "expires_in": -666
        }
        """.removeJsonWhitespace()

    val tokenIntrospectionResponseSuccessJson =
        """
        {
            "active": true
        }
        """.removeJsonWhitespace()

    val tokenIntrospectionResponseFailureJson =
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

    val invalidJson = "lykke til med denne responsen!"

    val tokenResponse =
        TokenResponse(
            accessToken = "there is no spoon",
            expiresInSeconds = -666,
        )

    val errorResponse =
        ErrorResponse(
            error = "blue pill or red pill?",
            errorDescription = "blue pill doink",
        )
}
