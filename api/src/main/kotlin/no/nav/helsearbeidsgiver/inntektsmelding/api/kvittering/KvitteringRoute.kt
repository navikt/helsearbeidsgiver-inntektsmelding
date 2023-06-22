package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import com.fasterxml.jackson.databind.JsonMappingException
import io.ktor.server.application.call
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.KvitteringResponse
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.authorize
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.mapInnsending
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.JacksonErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.fjernLedendeSlash
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondForbidden
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondNotFound
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import no.nav.helsearbeidsgiver.utils.json.parseJson
import org.valiktor.ConstraintViolationException
import kotlin.system.measureTimeMillis

private const val EMPTY_PAYLOAD = "{}"

fun RouteExtra.kvitteringRoute() {
    val kvitteringProducer = KvitteringProducer(connection)
    val tilgangProducer = TilgangProducer(connection)

    route.route(Routes.KVITTERING) {
        get {
            val forespoerselId = fjernLedendeSlash(call.parameters["uuid"].orEmpty())

            if (forespoerselId.isEmpty() || forespoerselId.length != 36) {
                "Ugyldig parameter: $forespoerselId".let {
                    logger.warn(it)
                    respondBadRequest(it, String.serializer())
                }
            }

            logger.info("Henter data for forespørselId: $forespoerselId")
            measureTimeMillis {
                try {
                    measureTimeMillis {
                        authorize(
                            forespørselId = forespoerselId,
                            tilgangProducer = tilgangProducer,
                            redisPoller = redis,
                            cache = tilgangCache
                        )
                    }.also {
                        logger.info("Authorize took $it")
                    }

                    val clientId = kvitteringProducer.publish(forespoerselId)
                    var resultat:String? = null
                    measureTimeMillis {
                        resultat = redis.getString(clientId, 10, 500)
                    }.also {
                        logger.info("redis polling took $it")
                    }
                    sikkerLogger.info("Forespørsel $forespoerselId ga resultat: $resultat")

                    if (resultat == EMPTY_PAYLOAD) {
                        // kvitteringService svarer med "{}" hvis det ikke er noen kvittering
                        respondNotFound("Kvittering ikke funnet for forespørselId: $forespoerselId", String.serializer())
                    } else {
                        measureTimeMillis {
                            val innsending = mapInnsending(Jackson.parseInntektsmeldingDokument(resultat))

                            respondOk(
                                Jackson.toJson(innsending).parseJson(),
                                JsonElement.serializer()
                            )
                        }.also {
                            logger.info("Mapping og respond took $it")
                        }
                    }
                } catch (e: ManglerAltinnRettigheterException) {
                    respondForbidden("Du har ikke rettigheter for organisasjon.", String.serializer())
                } catch (e: ConstraintViolationException) {
                    logger.info("Fikk valideringsfeil for forespørselId: $forespoerselId")
                    respondBadRequest(validationResponseMapper(e.constraintViolations), ValidationResponse.serializer())
                } catch (e: JsonMappingException) {
                    "Kunne ikke parse json-resultat for forespørselId: $forespoerselId".let {
                        logger.error(it)
                        sikkerLogger.error(it, e)
                        respondInternalServerError(JacksonErrorResponse(forespoerselId), JacksonErrorResponse.serializer())
                    }
                } catch (_: RedisPollerTimeoutException) {
                    logger.error("Fikk timeout for forespørselId: $forespoerselId")
                    respondInternalServerError(RedisTimeoutResponse(forespoerselId), RedisTimeoutResponse.serializer())
                }
            }.also {
                logger.info("api call took $it")
            }
        }
    }
}

private object Jackson {
    private val objectMapper = customObjectMapper()

    fun parseInntektsmeldingDokument(json: String): InntektsmeldingDokument =
        objectMapper.readValue(json, InntektsmeldingDokument::class.java)

    fun toJson(kvitteringResponse: KvitteringResponse): String =
        objectMapper.writeValueAsString(kvitteringResponse)
}
