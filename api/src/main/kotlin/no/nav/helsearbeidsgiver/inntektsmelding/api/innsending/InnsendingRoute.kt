package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.application
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Innsending
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.authorize
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.hentIdentitetsnummerFraLoginToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.JsonErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import org.valiktor.ConstraintViolationException
import java.util.UUID

fun RouteExtra.innsendingRoute() {
    val producer = InnsendingProducer(connection)
    val tilgangProducer = TilgangProducer(connection)

    route.route(Routes.INNSENDING + "/{forespoerselId}") {
        post {
            val forespoerselId = call.parameters["forespoerselId"]
                ?.runCatching(UUID::fromString)
                ?.getOrNull()

            if (forespoerselId != null) {
                try {
                    val request = call.receiveText().fromJson(Innsending.serializer()).let {
                        // TODO gjør denne sjekken ved opprettelse
                        if (it.fullLønnIArbeidsgiverPerioden?.utbetalerFullLønn == true) {
                            it.copy(
                                fullLønnIArbeidsgiverPerioden = FullLoennIArbeidsgiverPerioden(
                                    utbetalerFullLønn = true,
                                    begrunnelse = null,
                                    utbetalt = null
                                )
                            )
                        } else {
                            it
                        }
                    }

                    "Mottok innsending med forespørselId: $forespoerselId".let {
                        logger.info(it)
                        sikkerLogger.info("$it og request:\n$request")
                    }

                    authorize(
                        forespoerselId = forespoerselId,
                        tilgangProducer = tilgangProducer,
                        redisPoller = redis,
                        cache = tilgangCache
                    )

                    request.validate()
                    val innloggerFnr = hentIdentitetsnummerFraLoginToken(application.environment.config, call.request)
                    val clientId = producer.publish(forespoerselId, request, innloggerFnr)
                    logger.info("Publiserte til rapid med forespørselId: $forespoerselId og clientId=$clientId")

                    val resultat = redis.hent(clientId)
                    sikkerLogger.info("Fikk resultat: ${resultat.toPretty()}")

                    respond(HttpStatusCode.Created, InnsendingResponse(forespoerselId), InnsendingResponse.serializer())
                } catch (e: ConstraintViolationException) {
                    logger.info("Fikk valideringsfeil for forespørselId: $forespoerselId")
                    respondBadRequest(validationResponseMapper(e.constraintViolations), ValidationResponse.serializer())
                } catch (e: SerializationException) {
                    "Kunne ikke parse json for $forespoerselId".let {
                        logger.error(it)
                        sikkerLogger.error(it, e)
                        respondBadRequest(JsonErrorResponse(forespoerselId.toString()), JsonErrorResponse.serializer())
                    }
                } catch (e: RedisPollerTimeoutException) {
                    logger.info("Fikk timeout for forespørselId: $forespoerselId", e)
                    respondInternalServerError(RedisTimeoutResponse(forespoerselId), RedisTimeoutResponse.serializer())
                }
            } else {
                val feilmelding = "Forespørsel-ID mangler som stiparameter."

                logger.error(feilmelding)
                sikkerLogger.error(feilmelding)

                respondBadRequest(feilmelding, String.serializer())
            }
        }
    }
}
