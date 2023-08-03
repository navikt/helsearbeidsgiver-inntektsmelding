package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import com.fasterxml.jackson.databind.JsonMappingException
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.json.Jackson
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.authorize
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.JacksonErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.valiktor.ConstraintViolationException
import java.util.UUID

fun RouteExtra.innsendingRoute() {
    val producer = InnsendingProducer(connection)
    val tilgangProducer = TilgangProducer(connection)

    route.route(Routes.INNSENDING + "/{forespørselId}") {
        post {
            val forespoerselId = call.parameters["forespørselId"] ?: ""
            try {
                val request = Jackson.fromJson<InnsendingRequest>(call.receiveText())

                "Mottok innsending med forespørselId: $forespoerselId".let {
                    logger.info(it)
                    sikkerLogger.info("$it og request:\n$request")
                }

                authorize(
                    forespoerselId = forespoerselId.let(UUID::fromString),
                    tilgangProducer = tilgangProducer,
                    redisPoller = redis,
                    cache = tilgangCache
                )

                request.validate()

                val clientId = producer.publish(forespoerselId, request)
                logger.info("Publiserte til rapid med forespørselId: $forespoerselId og clientId=$clientId")

                val resultat = redis.getResultat(clientId)
                sikkerLogger.info("Fikk resultat: $resultat")

                //    val mapper = InnsendingMapper(forespoerselId, resultat)
                val mapper = InnsendingMapper(forespoerselId, resultat)
                respond(mapper.getStatus(), mapper.getResponse(), InnsendingResponse.serializer())
            } catch (e: ConstraintViolationException) {
                logger.info("Fikk valideringsfeil for forespørselId: $forespoerselId")
                respondBadRequest(validationResponseMapper(e.constraintViolations), ValidationResponse.serializer())
            } catch (e: JsonMappingException) {
                "Kunne ikke parse json-resultat for $forespoerselId".let {
                    logger.error(it)
                    sikkerLogger.error(it, e)
                    respondBadRequest(JacksonErrorResponse(forespoerselId), JacksonErrorResponse.serializer())
                }
            } catch (e: RedisPollerTimeoutException) {
                logger.info("Fikk timeout for forespørselId: $forespoerselId", e)
                respondInternalServerError(RedisTimeoutResponse(forespoerselId), RedisTimeoutResponse.serializer())
            }
        }
    }
}
