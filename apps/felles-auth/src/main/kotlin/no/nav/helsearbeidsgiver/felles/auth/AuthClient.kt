package no.nav.helsearbeidsgiver.felles.auth

import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.felles.utils.fromEnv
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

    internal suspend fun exchange(
        provider: IdentityProvider,
        target: String,
        userToken: String,
    ): TokenResponse =
        try {
            httpClient
                .submitForm(
                    url = tokenExchangeEndpoint,
                    formParameters =
                        parameters {
                            identityProvider(provider)
                            target(target)
                            userToken(userToken)
                        },
                ).body()
        } catch (e: ResponseException) {
            e.logAndRethrow()
        }

    internal suspend fun introspect(
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
