package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.pipeline.PipelineContext
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.authorize
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.valiktor.ConstraintViolationException

private val objectMapper = customObjectMapper()

fun RouteExtra.InnsendingRoute() {
    val producer = InnsendingProducer(connection)
    val tilgangProducer = TilgangProducer(connection)

    route.route(Routes.INNSENDING + "/{forespørselId}") {
        post {
            val request = receiveInnsendingRequest()

            val forespørselId = call.parameters["forespørselId"] ?: ""

            logger.info("Mottok innsending med forespørselId: $forespørselId")
            sikkerlogg.info("Mottok innsending med forespørselId: $forespørselId og request:\n$request")

            try {
                authorize(
                    forespørselId = forespørselId,
                    tilgangProducer = tilgangProducer,
                    redisPoller = redis,
                    cache = tilgangCache
                )

                request.validate()

                val transaksjonId = producer.publish(forespørselId, request)
                logger.info("Publiserte til rapid med forespørselId: $forespørselId og transaksjonId=$transaksjonId")

                val resultat = redis.getResultat(transaksjonId, 10, 500)
                sikkerlogg.info("Fikk resultat: $resultat")

                val mapper = InnsendingMapper(forespørselId, resultat)
                respond(mapper.getStatus(), mapper.getResponse(), InnsendingResponse.serializer())
            } catch (e: ConstraintViolationException) {
                logger.info("Fikk valideringsfeil for forespørselId: $forespørselId")
                respondBadRequest(validationResponseMapper(e.constraintViolations), ValidationResponse.serializer())
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout for forespørselId: $forespørselId")
                respondInternalServerError(RedisTimeoutResponse(forespørselId), RedisTimeoutResponse.serializer())
            }
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.receiveInnsendingRequest(): InnsendingRequest =
    objectMapper.readValue(call.receiveText(), InnsendingRequest::class.java)
