package no.nav.helsearbeidsgiver.inntektsmelding.api.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.application
import io.ktor.server.application.call
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.request.ApplicationRequest
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangData
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.security.token.support.core.jwt.JwtToken
import java.util.UUID

// TODO egen klasse med tilganglogikk (inkl. producer og cache)
fun PipelineContext<Unit, ApplicationCall>.authorize(
    forespoerselId: UUID,
    tilgangProducer: TilgangProducer,
    redisPoller: RedisPoller,
    cache: LocalCache<Tilgang>
) {
    val innloggerFnr = hentIdentitetsnummerFraLoginToken(application.environment.config, call.request)
    runBlocking {
        val tilgang = cache.get("$innloggerFnr:$forespoerselId") {
            logger.info("Fant ikke forespørsel i cache, ber om tilgangskontroll for $forespoerselId")

            val clientId = tilgangProducer.publish(forespoerselId, innloggerFnr)

            val resultat = redisPoller.hent(clientId)
                .fromJson(TilgangData.serializer())

            resultat.tilgang ?: throw ManglerAltinnRettigheterException()
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
        ?: throw IllegalAccessException("Mangler identitetstoken")
}

class ManglerAltinnRettigheterException : Exception()
