package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.authorize
import no.nav.helsearbeidsgiver.inntektsmelding.api.cache.LocalCache
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.valiktor.ConstraintViolationException

fun RouteExtra.InnsendingRoute(cache: LocalCache<Tilgang>) {
    val producer = InnsendingProducer(connection)
    val tilgangProducer = TilgangProducer(connection)

    route.route(Routes.INNSENDING + "/{forespørselId}") {
        post {
            val request = call.receive<InnsendingRequest>()
            val forespørselId = call.parameters["forespørselId"] ?: ""
            sikkerlogg.info("Mottok innsending $request for forespørselId: $forespørselId")
            try {
                logger.info("Fikk innsending med forespørselId: $forespørselId")
                authorize(
                    forespørselId = forespørselId,
                    tilgangProducer = tilgangProducer,
                    redisPoller = redis,
                    cache = cache
                )
                request.validate()
                producer.publish(forespørselId, request)
                logger.info("Publiserte til Rapid med forespørselId: $forespørselId")
                val resultat = redis.getResultat(forespørselId, 10, 500)
                sikkerlogg.info("Fikk resultat: $resultat")
                val mapper = InnsendingMapper(forespørselId, resultat)
                call.respond(mapper.getStatus(), mapper.getResponse())
            } catch (e: ConstraintViolationException) {
                logger.info("Fikk valideringsfeil for forespørselId: $forespørselId")
                call.respond(HttpStatusCode.BadRequest, validationResponseMapper(e.constraintViolations))
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout for forespørselId: $forespørselId")
                call.respond(HttpStatusCode.InternalServerError, RedisTimeoutResponse(forespørselId))
            }
        }
    }
}
