package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.InnsendingFeilet
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import org.valiktor.ConstraintViolationException

fun Route.preutfyltRoute(producer: PreutfyltProducer, poller: RedisPoller, objectMapper: ObjectMapper) {
    route("/preutfyll") {
        post {
            val request = call.receive<PreutfyllRequest>()
            var uuid = "ukjent uuid"
            sikkerlogg.info("Mottok preutfylt $request")
            try {
                request.validate()
                uuid = producer.publish(request)
                logger.info("Publiserte behov uuid: $uuid")
                val data = poller.getValue(uuid, 5, 500)
                sikkerlogg.info("Fikk resultat for $uuid : $data")
                val resultat = objectMapper.readValue<Resultat>(data)
                sikkerlogg.info("Klarte tolke resultat for $uuid : $resultat")
                val mapper = PreutfyltMapper(uuid, resultat, request)
                sikkerlogg.info("Klarte mappe resultat for $uuid : $resultat")
                val res = mapper.getResponse()
                sikkerlogg.info("Klarte response resultat for $uuid : $res")
                call.respond(mapper.getStatus(), objectMapper.writeValueAsString(res))
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
