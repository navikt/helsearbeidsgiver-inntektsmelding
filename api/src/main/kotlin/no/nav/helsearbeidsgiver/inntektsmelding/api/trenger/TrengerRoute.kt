package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import io.ktor.server.routing.post
import io.ktor.server.routing.route
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

fun RouteExtra.TrengerRoute() {
    val trengerProducer = TrengerProducer(connection)

    route.route(Routes.TRENGER) {
        post {
            val request = receive(TrengerRequest.serializer())

            logger.info("Henter data for uuid: ${request.uuid}")

            try {
                request.validate()

                val uuid = trengerProducer.publish(request)

                val resultat = redis.getResultat(uuid.toString(), 10, 500)
                sikkerlogg.info("Fikk resultat: $resultat")

                val mapper = TrengerMapper(resultat)
                respond(mapper.getStatus(), mapper.getResponse(), TrengerResponse.serializer())
            } catch (e: ConstraintViolationException) {
                logger.info("Fikk valideringsfeil for ${request.uuid}")
                respondBadRequest(validationResponseMapper(e.constraintViolations), ValidationResponse.serializer())
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout for ${request.uuid}")
                respondInternalServerError(RedisTimeoutResponse(request.uuid), RedisTimeoutResponse.serializer())
            }
        }
    }
}
