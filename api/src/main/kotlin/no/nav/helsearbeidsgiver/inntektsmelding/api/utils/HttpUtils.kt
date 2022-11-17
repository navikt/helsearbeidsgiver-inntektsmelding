package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.AuthScheme
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.authorization
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext
import no.nav.helsearbeidsgiver.inntektsmelding.api.Auth
import no.nav.security.token.support.core.jwt.JwtToken

fun PipelineContext<Unit, ApplicationCall>.identitetsnummer(): String =
    call.request
        .authorization()
        ?.removePrefix("${AuthScheme.Bearer} ")
        ?.let(::JwtToken)
        ?.jwtTokenClaims
        ?.getStringClaim(Auth.CLAIM_PID)!!

suspend inline fun <reified T : Any> PipelineContext<Unit, ApplicationCall>.respondOk(message: T) {
    call.respond(HttpStatusCode.OK, message)
}

suspend inline fun <reified T : Any> PipelineContext<Unit, ApplicationCall>.respondInternalServerError(message: T) {
    call.respond(HttpStatusCode.InternalServerError, message)
}
