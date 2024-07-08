package no.nav.helsearbeidsgiver.inntektsmelding.api.auth

import io.ktor.http.auth.AuthScheme
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.authorization
import no.nav.helsearbeidsgiver.inntektsmelding.api.Auth
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.security.token.support.core.jwt.JwtToken

fun ApplicationRequest.lesFnrFraAuthToken(): Fnr {
    val authToken =
        authorization()?.removePrefix("${AuthScheme.Bearer} ")
            ?: throw IllegalAccessException("Mangler autorisasjonsheader.")

    val pid = JwtToken(authToken).jwtTokenClaims.getStringClaim(Auth.CLAIM_PID)

    val fnr = pid ?: JwtToken(authToken).subject

    if (!Fnr.erGyldig(fnr)) {
        throw IllegalAccessException("Fnr i autorisasjonsheader er ugyldig.")
    }

    return Fnr(fnr)
}

class ManglerAltinnRettigheterException : Exception()
