package no.nav.helsearbeidsgiver.inntektsmelding.api.auth

import io.ktor.server.application.* // ktlint-disable no-wildcard-imports
import io.ktor.server.application.ApplicationCall
import io.ktor.server.config.* // ktlint-disable no-wildcard-imports
import io.ktor.server.request.* // ktlint-disable no-wildcard-imports
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.cache.LocalCache
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.security.token.support.core.jwt.JwtToken

fun PipelineContext<Unit, ApplicationCall>.authorize(
    forespørselId: String,
    tilgangProducer: TilgangProducer,
    redisPoller: RedisPoller,
    cache: LocalCache<Tilgang>
) {
    val innloggerFnr = hentIdentitetsnummerFraLoginToken(application.environment.config, call.request)
    runBlocking {
        val tilgang = cache.get("$innloggerFnr:$forespørselId") {
            logger.info("Fant ikke forespøresel i cache ber om tilgangskontroll for $forespørselId")
            val tilgangId = tilgangProducer.publish(innloggerFnr, forespørselId)
            val resultatTilgang = redisPoller.getResultat(tilgangId.toString(), 10, 500)
            if (resultatTilgang.TILGANGSKONTROLL == null) {
                throw ManglerAltinnRettigheterException()
            } else if (resultatTilgang.TILGANGSKONTROLL!!.value == null) {
                throw ManglerAltinnRettigheterException()
            } else {
                resultatTilgang.TILGANGSKONTROLL!!.value!!
            }
        }
        if (tilgang != Tilgang.HAR_TILGANG) throw ManglerAltinnRettigheterException()
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
