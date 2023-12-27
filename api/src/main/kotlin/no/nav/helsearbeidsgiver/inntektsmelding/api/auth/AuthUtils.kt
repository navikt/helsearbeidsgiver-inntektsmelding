package no.nav.helsearbeidsgiver.inntektsmelding.api.auth

import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.authorization
import no.nav.security.token.support.core.jwt.JwtToken

fun ApplicationRequest.lesFnrFraAuthToken(): String {
    val authToken = authorization()?.removePrefix("Bearer ")
        ?: throw IllegalAccessException("Mangler autorisasjonsheader.")

    val pid = JwtToken(authToken).jwtTokenClaims.get("pid")?.toString()

    return pid ?: JwtToken(authToken).subject
}

class ManglerAltinnRettigheterException : Exception()
