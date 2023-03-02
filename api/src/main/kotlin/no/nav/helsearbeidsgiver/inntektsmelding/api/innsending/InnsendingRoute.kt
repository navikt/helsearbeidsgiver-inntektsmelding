package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.helsearbeidsgiver.felles.inntektsmelding.request.InnsendingRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.receive
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.valiktor.ConstraintViolationException

fun RouteExtra.InnsendingRoute() {
    val producer = InnsendingProducer(connection)

    route.route(Routes.INNSENDING) {
        post {
            val request = receive(InnsendingRequest.serializer())

            logger.info("Mottok innsending.")
            sikkerlogg.info("Mottok innsending:\n$request")

            var uuid = "ukjent uuid"

            try {
                request.validate()

                uuid = producer.publish(request)
                logger.info("Publiserte til Rapid med uuid: $uuid")

                val resultat = redis.getResultat(uuid, 10, 500)
                sikkerlogg.info("Fikk resultat: $resultat")

                val mapper = InnsendingMapper(uuid, resultat)
                respond(mapper.getStatus(), mapper.getResponse(), InnsendingResponse.serializer())
            } catch (e: ConstraintViolationException) {
                logger.info("Fikk valideringsfeil for $uuid")
                respondBadRequest(validationResponseMapper(e.constraintViolations), ValidationResponse.serializer())
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout for $uuid")
                respondInternalServerError(RedisTimeoutResponse(uuid), RedisTimeoutResponse.serializer())
            }
        }
    }
}
