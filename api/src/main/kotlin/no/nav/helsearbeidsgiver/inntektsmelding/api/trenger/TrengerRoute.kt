package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.authorize
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.receive
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondForbidden
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.valiktor.ConstraintViolationException

fun RouteExtra.trengerRoute() {
    val trengerProducer = TrengerProducer(connection)
    val tilgangProducer = TilgangProducer(connection)

    route.route(Routes.TRENGER) {
        post {
            val request = receive(TrengerRequest.serializer())

            logger.info("Henter data for uuid: ${request.uuid}")

            try {
                request.validate()

                authorize(
                    forespørselId = request.uuid,
                    tilgangProducer = tilgangProducer,
                    redisPoller = redis,
                    cache = tilgangCache
                )

                val trengerId = trengerProducer.publish(request)
                val resultat = redis.getResultat(trengerId.toString(), 10, 500)
                sikkerLogger.info("Fikk resultat: $resultat")

                val mapper = TrengerMapper(resultat)
                respond(mapper.getStatus(), mapper.getResponse(), TrengerResponse.serializer())
            } catch (e: ManglerAltinnRettigheterException) {
                respondForbidden("Du har ikke rettigheter for organisasjon.", String.serializer())
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
