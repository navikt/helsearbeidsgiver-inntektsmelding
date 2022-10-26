package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.InnsendingFeilet
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import org.valiktor.ConstraintViolationException

fun RouteExtra.preutfyltRoute() {
    val producer = PreutfyltProducer(connection)

    route.route("/preutfyll") {
        post {
            val request = call.receive<PreutfyllRequest>()
            var uuid = "ukjent uuid"
            sikkerlogg.info("Mottok preutfylt $request")
            try {
                request.validate()
                uuid = producer.publish(request)
                logger.info("Publiserte behov uuid: $uuid")
                val resultat = redis.getResultat(uuid, 5, 500)
                sikkerlogg.info("Fikk resultat for $uuid : $resultat")
                val mapper = PreutfyltMapper(uuid, resultat, request)
                sikkerlogg.info("Klarte mappe resultat for $uuid : $resultat")
                call.respond(mapper.getStatus(), mapper.getResponse())
            } catch (ex2: ConstraintViolationException) {
                logger.info("Fikk valideringsfeil for $uuid")
                call.respond(HttpStatusCode.BadRequest, ex2.constraintViolations)
            } catch (ex: RedisPollerTimeoutException) {
                logger.info("Fikk timeout for $uuid")
                call.respond(HttpStatusCode.InternalServerError, InnsendingFeilet(uuid, "Brukte for lang tid"))
            }
        }
    }
}
