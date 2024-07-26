package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.v2.IssuerConfig
import no.nav.security.token.support.v2.TokenSupportConfig
import no.nav.security.token.support.v2.tokenValidationSupport

object Auth {
    const val IDPORTEN_ISSUER = "idporten-issuer"
    const val TOKENX_ISSUER = "tokenx-issuer"
    const val CLAIM_PID = "pid"
    const val IDPORTEN_VALIDATION = "idporten-validation"
    const val TOKENX_VALIDATION = "tokenx-validation"
}

private val pidRegex = Regex("\\d{11}")

fun Application.customAuthentication() {
    val idportenConfig =
        TokenSupportConfig(
            IssuerConfig(
                name = Auth.IDPORTEN_ISSUER,
                discoveryUrl = Env.Auth.discoveryUrl,
                acceptedAudience = Env.Auth.acceptedAudience,
            ),
        )

    val tokenXConfig =
        TokenSupportConfig(
            IssuerConfig(
                name = Auth.TOKENX_ISSUER,
                discoveryUrl = Env.Auth.TokenX.discoveryUrl,
                acceptedAudience = Env.Auth.TokenX.acceptedAudience,
            ),
        )

    authentication {
        tokenValidationSupport(
            name = Auth.IDPORTEN_VALIDATION,
            config = idportenConfig,
            additionalValidation = TokenValidationContext::containsPid,
        )
        tokenValidationSupport(
            name = Auth.TOKENX_VALIDATION,
            config = tokenXConfig,
            additionalValidation = {
                it.containsPid(Auth.TOKENX_ISSUER)
            },
        )
    }
}

private fun TokenValidationContext.containsPid(issuer: String = Auth.IDPORTEN_ISSUER): Boolean =
    getClaims(issuer)
        .getStringClaim(Auth.CLAIM_PID)
        .matches(pidRegex)
