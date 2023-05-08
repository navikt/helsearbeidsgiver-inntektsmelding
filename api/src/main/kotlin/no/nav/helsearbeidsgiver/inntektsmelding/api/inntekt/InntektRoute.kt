package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.authorize
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.valiktor.ConstraintViolationException

// TODO bruk kotlinx
fun RouteExtra.InntektRoute() {
    val inntektProducer = InntektProducer(connection)
    val tilgangProducer = TilgangProducer(connection)

    route.route(Routes.INNTEKT) {
        post {
            val request = call.receive<InntektRequest>()
            val uuid = request.forespoerselId
            val fom = request.skjaeringstidspunkt
            authorize(
                forespørselId = uuid.toString(),
                tilgangProducer = tilgangProducer,
                redisPoller = redis,
                cache = tilgangCache
            )
            logger.info("Henter oppdatert inntekt for uuid: $uuid og dato: $fom")
            try {
                val loesningId = inntektProducer.publish(request).toString()
                val resultat = redis.getResultat(loesningId, 10, 500)
                sikkerlogg.info("Fikk resultat: $resultat")
                val mapper = InntektMapper(resultat)
                call.respond(mapper.getStatus(), mapper.getResponse())
            } catch (e: ManglerAltinnRettigheterException) {
                call.respond(HttpStatusCode.Forbidden, "Du har ikke rettigheter for organisasjon.")
            } catch (e: ConstraintViolationException) {
                logger.info("Fikk valideringsfeil for $request.uuid")
                call.respond(HttpStatusCode.BadRequest, validationResponseMapper(e.constraintViolations))
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout for $request.uuid")
                call.respond(HttpStatusCode.InternalServerError, RedisTimeoutResponse(request.forespoerselId.toString()))
            }
        }
    }
}
