package no.nav.helsearbeidsgiver.inntektsmelding.dokumentproxy

import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.request.host
import io.ktor.server.request.uri
import io.ktor.server.response.respondRedirect
import no.nav.hag.simba.utils.auth.AuthClient
import no.nav.hag.simba.utils.auth.IdentityProvider
import no.nav.helsearbeidsgiver.utils.log.logger
import org.slf4j.Logger

fun AuthenticationConfig.texas(
    name: String? = null,
    configure: TexasAuthenticationProvider.Config.() -> Unit,
) {
    register(TexasAuthenticationProvider.Config(name).apply(configure).build())
}
class TexasAuthenticationProvider(
    config: Config,
) : AuthenticationProvider(config) {
    class Config internal constructor(
        name: String?,
    ) : AuthenticationProvider.Config(name) {
        lateinit var client: AuthClient
        var logger: Logger = logger()
        //var ingress: String = ""

        internal fun build() = TexasAuthenticationProvider(this)
    }

    private val client = config.client
    private val logger = config.logger
    //private val ingress = config.ingress

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val applicationCall = context.call
        val token =
            (applicationCall.request.parseAuthorizationHeader() as? HttpAuthHeader.Single)
                ?.takeIf { header -> header.authScheme.lowercase() == AuthScheme.Bearer.lowercase() }
                ?.blob

        if (token == null) {
            logger.warn("unauthenticated: no Bearer token found in Authorization header")
            context.loginChallenge(AuthenticationFailedCause.NoCredentials)
            return
        }

        val introspectResponse =
            try {
                client.introspect(IdentityProvider.IDPORTEN, token)
            } catch (e: Exception) {
                // TODO(user): You should handle the specific exceptions that can be thrown by the HTTP client, e.g. retry on network errors and so on
                logger.error("unauthenticated: introspect request failed", e)
                context.loginChallenge(AuthenticationFailedCause.Error(e.message ?: "introspect request failed"))
                return
            }

        if (!introspectResponse.active) {
            logger.warn("unauthenticated: ${introspectResponse.error}")
            context.loginChallenge(AuthenticationFailedCause.InvalidCredentials)
            return
        }

        context.principal(
            TexasPrincipal(
                token = token,
            ),
        )
    }


    private fun AuthenticationContext.loginChallenge(cause: AuthenticationFailedCause) {
        challenge("Texas", cause) { authenticationProcedureChallenge, call ->
            val target = call.loginUrl()
            logger.info("unauthenticated: redirecting to '$target'")
            call.respondRedirect(target)
            authenticationProcedureChallenge.complete()
        }
    }

    /**
     * loginUrl constructs a URL string that points to the login endpoint (Wonderwall) for redirecting a request.
     * It also indicates that the user should be redirected back to the original request path after authentication
     */
    private fun ApplicationCall.loginUrl(): String {
        val host =
    //        ingress.ifEmpty(defaultValue = {
                "${this.request.local.scheme}://${this.request.host()}"
      //      })

        return "$host/oauth2/login?redirect=${this.request.uri}"
    }
}

data class TexasPrincipal(
    //val claims: Map<String, Any?>,TODO skal claims være definert her?
    val token: String,
)
