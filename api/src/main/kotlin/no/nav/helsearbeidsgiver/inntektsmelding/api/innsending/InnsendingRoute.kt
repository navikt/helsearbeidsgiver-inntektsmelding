package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import org.valiktor.ConstraintViolationException

fun Route.innsendingRoute(producer: InnsendingProducer, poller: RedisPoller) {
    route("/inntektsmelding") {
        post {
            val request = call.receive<InntektsmeldingRequest>()
            var uuid = "ukjent uuid"
            sikkerlogg.info("Mottok innsending $request")
            try {
                logger.info("Fikk innsending")
                request.validate()
                uuid = producer.publish(request)
                logger.info("Publiserte til Rapid med uuid: $uuid")
                val resultat = poller.getResultat(uuid, 5, 500)
                sikkerlogg.info("Fikk value: $resultat")
                val mapper = InnsendingMapper(uuid, resultat)
                call.respond(mapper.getStatus(), mapper.getResponse())
            } catch (ex2: ConstraintViolationException) {
                logger.info("Valideringsfeil!")
                call.respond(HttpStatusCode.BadRequest, ex2.constraintViolations)
            } catch (ex: RedisPollerTimeoutException) {
                logger.info("Fikk timeout!")
                call.respond(HttpStatusCode.InternalServerError, InnsendingFeilet(uuid, "Brukte for lang tid"))
            }
        }
    }
}
