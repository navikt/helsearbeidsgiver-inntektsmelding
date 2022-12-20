package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

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
import no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt.PreutfyltMapper
import no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt.PreutfyltProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt.PreutfyltRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.valiktor.ConstraintViolationException

fun RouteExtra.TrengerRoute() {
    val preutfyltProducer = PreutfyltProducer(connection)

    route.route(Routes.TRENGER) {
        post {
            val request = call.receive<TrengerRequest>()
            var uuid = "ukjent uuid"
            sikkerlogg.info("Mottok Trenger $request")
            try {
                request.validate()
                // TODO Hent orgnr og fnr dynamisk
                val fnr = "22506614191"
                val orgnr = "810007842"
                //
                val preutfyltRequest = PreutfyltRequest(orgnr, fnr)
                uuid = preutfyltProducer.publish(preutfyltRequest)
                logger.info("Publiserte behov uuid: $uuid")
                val resultat = redis.getResultat(uuid, 10, 500)
                sikkerlogg.info("Fikk resultat for $uuid : $resultat")
                val mapper = PreutfyltMapper(uuid, resultat, preutfyltRequest)
                sikkerlogg.info("Klarte mappe resultat for $uuid : $resultat")
                call.respond(mapper.getStatus(), mapper.getResponse())
            } catch (e: ConstraintViolationException) {
                logger.info("Fikk valideringsfeil for $uuid")
                call.respond(HttpStatusCode.BadRequest, validationResponseMapper(e.constraintViolations))
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout for $uuid")
                call.respond(HttpStatusCode.InternalServerError, RedisTimeoutResponse(uuid))
            }
        }
    }
}
