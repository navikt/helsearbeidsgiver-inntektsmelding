package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.prometheus.client.Summary
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.lesFnrFraAuthToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.JsonErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import org.valiktor.ConstraintViolationException
import java.util.UUID
import kotlin.system.measureTimeMillis

fun Route.innsendingRoute(
    rapid: RapidsConnection,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val producer = InnsendingProducer(rapid)
    val redisPoller = RedisStoreClassSpecific(redisConnection, RedisPrefix.InnsendingService).let(::RedisPoller)

    val requestLatency =
        Summary
            .build()
            .name("simba_innsending_latency_seconds")
            .help("innsending endpoint latency in seconds")
            .register()

    post(Routes.INNSENDING + "/{forespoerselId}") {
        val transaksjonId = UUID.randomUUID()

        val forespoerselId =
            call.parameters["forespoerselId"]
                ?.runCatching(UUID::fromString)
                ?.getOrNull()

        if (forespoerselId != null) {
            val requestTimer = requestLatency.startTimer()
            measureTimeMillis {
                try {
                    val request =
                        call
                            .receiveText()
                            .parseJson()
                            .also { json ->
                                "Mottok innsending med forespørselId: $forespoerselId".let {
                                    logger.info(it)
                                    sikkerLogger.info("$it og request:\n$json")
                                }
                            }.fromJson(Innsending.serializer())

                    tilgangskontroll.validerTilgangTilForespoersel(call.request, forespoerselId)

                    request.validate()
                    val innloggerFnr = call.request.lesFnrFraAuthToken()
                    producer.publish(transaksjonId, forespoerselId, request, innloggerFnr)

                    val resultatJson = redisPoller.hent(transaksjonId).fromJson(ResultJson.serializer())
                    sikkerLogger.info("Fikk resultat for innsending:\n$resultatJson")

                    if (resultatJson.success != null) {
                        respond(HttpStatusCode.Created, InnsendingResponse(forespoerselId), InnsendingResponse.serializer())
                    } else {
                        val feilmelding = resultatJson.failure?.fromJson(String.serializer()) ?: Tekst.TEKNISK_FEIL_FORBIGAAENDE
                        respondInternalServerError(feilmelding, String.serializer())
                    }
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
            }.also {
                requestTimer.observeDuration()
                logger.info("Api call to ${Routes.INNSENDING} took $it ms")
            }
        } else {
            val feilmelding = "Forespørsel-ID mangler som stiparameter."

            logger.error(feilmelding)
            sikkerLogger.error(feilmelding)

            respondBadRequest(feilmelding, String.serializer())
        }
    }
}
