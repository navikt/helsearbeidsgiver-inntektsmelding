package no.nav.helsearbeidsgiver.inntektsmelding.api.authorization

import io.ktor.server.application.* // ktlint-disable no-wildcard-imports
import io.ktor.server.application.ApplicationCall
import io.ktor.server.config.* // ktlint-disable no-wildcard-imports
import io.ktor.server.request.* // ktlint-disable no-wildcard-imports
import io.ktor.util.pipeline.PipelineContext
import no.nav.security.token.support.core.jwt.JwtToken

fun PipelineContext<Unit, ApplicationCall>.authorize(authorizer: AltinnAuthorizer, arbeidsgiverId: String) {
    val identitetsnummer = hentIdentitetsnummerFraLoginToken(application.environment.config, call.request)
    if (!authorizer.hasAccess(identitetsnummer, arbeidsgiverId)) {
        throw ManglerAltinnRettigheterException()
    }
}
fun hentIdentitetsnummerFraLoginToken(config: ApplicationConfig, request: ApplicationRequest): String {
    val tokenString = getTokenString(config, request)
    val pid = JwtToken(tokenString).jwtTokenClaims.get("pid")
    return pid?.toString() ?: JwtToken(tokenString).subject
}

private fun getTokenString(config: ApplicationConfig, request: ApplicationRequest): String {
    return request.headers["Authorization"]?.replaceFirst("Bearer ", "")
        ?: request.cookies[config.configList("no.nav.security.jwt.issuers")[0].property("cookie_name").getString()]
        ?: throw IllegalAccessException("Du må angi et identitetstoken som i cookie eller i Authorization-headeren")
}

class ManglerAltinnRettigheterException : Exception()
