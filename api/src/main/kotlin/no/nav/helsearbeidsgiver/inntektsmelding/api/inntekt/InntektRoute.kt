package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
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
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondForbidden
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.valiktor.ConstraintViolationException

fun RouteExtra.inntektRoute() {
    val inntektProducer = InntektProducer(connection)
    val tilgangProducer = TilgangProducer(connection)

    route.route(Routes.INNTEKT) {
        post {
            val request = call.receive<InntektRequest>()

            authorize(
                forespørselId = request.forespoerselId.toString(),
                tilgangProducer = tilgangProducer,
                redisPoller = redis,
                cache = tilgangCache
            )

            "Henter oppdatert inntekt for forespørselId: ${request.forespoerselId}".let {
                logger.info(it)
                sikkerLogger.info("$it og request:\n$request")
            }

            try {
                val transaksjonId = inntektProducer.publish(request).toString()

                val resultat = redis.getResultat(transaksjonId, 10, 500)
                sikkerLogger.info("Fikk resultat: $resultat")

                val mapper = InntektMapper(resultat)
                call.respond(mapper.getStatus(), mapper.getResponse())
            } catch (e: ManglerAltinnRettigheterException) {
                respondForbidden("Du har ikke rettigheter for organisasjon.", String.serializer())
            } catch (e: ConstraintViolationException) {
                logger.info("Fikk valideringsfeil for forespørselId: ${request.forespoerselId}")
                sikkerLogger.info("Fikk valideringsfeil for forespørselId: ${request.forespoerselId}")
                respondBadRequest(validationResponseMapper(e.constraintViolations), ValidationResponse.serializer())
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout for forespørselId: ${request.forespoerselId}")
                sikkerLogger.info("Fikk timeout for forespørselId: ${request.forespoerselId}")
                respondInternalServerError(RedisTimeoutResponse(request.forespoerselId.toString()), RedisTimeoutResponse.serializer())
            }
        }
    }
}
