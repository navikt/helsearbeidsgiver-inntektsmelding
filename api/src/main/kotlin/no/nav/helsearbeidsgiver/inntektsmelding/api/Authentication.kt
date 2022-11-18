package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import no.nav.helsearbeidsgiver.felles.fromEnv
import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.v2.IssuerConfig
import no.nav.security.token.support.v2.TokenSupportConfig
import no.nav.security.token.support.v2.tokenValidationSupport

object Auth {
    const val ISSUER = "loginservice-issuer"
    const val CLAIM_PID = "pid"
}

private val pidRegex = Regex("\\d{11}")

fun Application.customAuthentication() {
    val config = TokenSupportConfig(
        IssuerConfig(
            name = Auth.ISSUER,
            discoveryUrl = "LOGINSERVICE_IDPORTEN_DISCOVERY_URL".fromEnv(),
            acceptedAudience = "LOGINSERVICE_IDPORTEN_AUDIENCE".fromEnv().let(::listOf)
        )
    )

    authentication {
        tokenValidationSupport(
            config = config,
            additionalValidation = TokenValidationContext::containsPid
        )
    }
}

private fun TokenValidationContext.containsPid(): Boolean =
    getClaims(Auth.ISSUER)
        .getStringClaim(Auth.CLAIM_PID)
        .matches(pidRegex)
