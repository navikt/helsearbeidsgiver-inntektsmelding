package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.valiktor.ConstraintViolationException

fun RouteExtra.InnsendingRoute() {
    val producer = InnsendingProducer(connection)

    route.route(Routes.INNSENDING) {
        post {
            val request = call.receive<InnsendingRequest>()
            var uuid = "ukjent uuid"
            sikkerlogg.info("Mottok innsending $request")
            try {
                logger.info("Fikk innsending")
                request.validate()
                uuid = producer.publish(request)
                logger.info("Publiserte til Rapid med uuid: $uuid")
                val resultat = redis.getResultat(uuid, 10, 500)
                sikkerlogg.info("Fikk resultat: $resultat")
                val mapper = InnsendingMapper(uuid, resultat)
                call.respond(mapper.getStatus(), mapper.getResponse())
            } catch (e: ConstraintViolationException) {
                logger.info("Fikk valideringsfeil for $uuid")
                call.respond(HttpStatusCode.BadRequest, validationResponseMapper(e.constraintViolations))
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout for $uuid")
                call.respond(HttpStatusCode.InternalServerError, InnsendingFeilet(uuid, "Brukte for lang tid"))
            }
        }
    }
}
