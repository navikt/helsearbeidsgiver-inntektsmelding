package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.v2.IssuerConfig
import no.nav.security.token.support.v2.TokenSupportConfig
import no.nav.security.token.support.v2.tokenValidationSupport

object Auth {
    const val ISSUER = "idporten-issuer"
    const val CLAIM_PID = "pid"
}

private val pidRegex = Regex("\\d{11}")

fun Application.customAuthentication() {
    val config =
        TokenSupportConfig(
            IssuerConfig(
                name = Auth.ISSUER,
                discoveryUrl = Env.Auth.discoveryUrl,
                acceptedAudience = Env.Auth.acceptedAudience,
            ),
        )

    val tokenXConfig =
        TokenSupportConfig(
            IssuerConfig(
                name = "tokenx-issuer",
                discoveryUrl = Env.Auth.TokenX.discoveryUrl,
                acceptedAudience = Env.Auth.TokenX.acceptedAudience,
            ),
        )

    authentication {
        tokenValidationSupport(
            "idporten-validation",
            config = config,
            additionalValidation = TokenValidationContext::containsPid,
        )
        tokenValidationSupport(
            "tokenx-validation",
            config = tokenXConfig,
            additionalValidation = TokenValidationContext::containsPid,
        )
    }
}

private fun TokenValidationContext.containsPid(): Boolean =
    getClaims(Auth.ISSUER)
        .getStringClaim(Auth.CLAIM_PID)
        .matches(pidRegex)
