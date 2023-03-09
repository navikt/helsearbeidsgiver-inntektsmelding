package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.valiktor.ConstraintViolationException

fun RouteExtra.InntektRoute() {
    val inntektProducer = InntektProducer(connection)

    route.route(Routes.INNTEKT) {
        post {
            val request = call.receive<InntektRequest>()
            val uuid = request.uuid
            val fom = request.fom
            logger.info("Henter oppdatert inntekt for uuid: $uuid og dato: $fom")
            try {
                request.validate()
                inntektProducer.publish(request)
                val resultat = redis.getResultat(request.requestKey(), 10, 500)

                sikkerlogg.info("Fikk resultat: $resultat")
                val mapper = InntektMapper(resultat)
                call.respond(mapper.getStatus(), mapper.getResponse())
            } catch (e: ConstraintViolationException) {
                logger.info("Fikk valideringsfeil for $request.uuid")
                call.respond(HttpStatusCode.BadRequest, validationResponseMapper(e.constraintViolations))
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout for $request.uuid")
                call.respond(HttpStatusCode.InternalServerError, RedisTimeoutResponse(request.uuid))
            }
        }
    }
}
