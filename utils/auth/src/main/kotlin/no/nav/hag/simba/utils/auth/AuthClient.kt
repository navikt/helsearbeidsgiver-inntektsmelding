package no.nav.hag.simba.utils.auth

import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import kotlinx.coroutines.runBlocking
import no.nav.hag.simba.utils.felles.utils.fromEnv
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

/** Krever `entraIdEnabled: true` i konfig. */
class AuthClient {
    private val sikkerLogger = sikkerLogger()
    private val httpClient = createHttpClient()

    private val tokenEndpoint = "NAIS_TOKEN_ENDPOINT".fromEnv()
    private val tokenExchangeEndpoint = "NAIS_TOKEN_EXCHANGE_ENDPOINT".fromEnv()
    private val tokenIntrospectionEndpoint = "NAIS_TOKEN_INTROSPECTION_ENDPOINT".fromEnv()

    fun tokenGetter(
        identityProvider: IdentityProvider,
        target: String,
    ): () -> String =
        {
            runBlocking {
                token(identityProvider, target).accessToken
            }
        }

    fun tokenExchanger(
        identityProvider: IdentityProvider,
        target: String,
        userToken: String,
    ): String =
        runBlocking {
            sikkerLogger.info("Henter exchangetoken for target=$target og provider=${identityProvider.verdi}")
            logger().info("Henter exchangetoken for target=$target og provider=${identityProvider.verdi}")
            exchange(identityProvider, target, userToken).accessToken
        }

    internal suspend fun token(
        provider: IdentityProvider,
        target: String,
    ): TokenResponse =
        try {
            httpClient
                .submitForm(
                    url = tokenEndpoint,
                    formParameters =
                        parameters {
                            identityProvider(provider)
                            target(target)
                        },
                ).body()
        } catch (e: ResponseException) {
            e.logAndRethrow()
        }

    suspend fun exchange(
        provider: IdentityProvider,
        target: String,
        userToken: String,
    ): TokenResponse =
        try {
            logger().info("Bytter token mot target=$target og provider=${provider.verdi}")
            sikkerLogger.info("Bytter token mot target=$target og provider=${provider.verdi}")
            val tokenResponse =
                httpClient
                    .submitForm(
                        url = tokenExchangeEndpoint,
                        formParameters =
                            parameters {
                                identityProvider(provider)
                                target(target)
                                userToken(userToken)
                            },
                    ).body<TokenResponse>()
            logger().info(
                "Byttet token mot target=$target og provider=${provider.verdi} - fikk access token med gyldighet i ${tokenResponse.expiresInSeconds} s",
            )
            sikkerLogger.info(
                "Byttet token mot target=$target og provider=${provider.verdi} - fikk access token med gyldighet i ${tokenResponse.expiresInSeconds} s",
            )
            tokenResponse
        } catch (e: ResponseException) {
            e.logAndRethrow()
        }

    suspend fun introspect(
        provider: IdentityProvider,
        accessToken: String,
    ): TokenIntrospectionResponse =
        httpClient
            .submitForm(
                url = tokenIntrospectionEndpoint,
                formParameters =
                    parameters {
                        identityProvider(provider)
                        token(accessToken)
                    },
            ).body()

    private suspend fun ResponseException.logAndRethrow(): Nothing {
        val error = response.body<ErrorResponse>()
        val msg = "Klarte ikke hente token. Feilet med status '${response.status}' og feilmelding '${error.errorDescription}'."

        sikkerLogger.error(msg)
        throw this
    }
}
