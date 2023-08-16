package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import com.fasterxml.jackson.databind.JsonMappingException
import io.ktor.server.application.call
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.prometheus.client.Summary
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.KvitteringResponse
import no.nav.helsearbeidsgiver.felles.json.Jackson
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.authorize
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
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.util.UUID
import kotlin.system.measureTimeMillis

private const val EMPTY_PAYLOAD = "{}"

fun RouteExtra.kvitteringRoute() {
    val kvitteringProducer = KvitteringProducer(connection)
    val tilgangProducer = TilgangProducer(connection)
    val requestLatency = Summary.build()
        .name("simba_kvittering_latency_seconds")
        .help("kvittering endpoint latency in seconds")
        .register()

    route.route(Routes.KVITTERING) {
        get {
            val forespoerselId = call.parameters["uuid"]
                ?.let(::fjernLedendeSlash)
                ?.runCatching(UUID::fromString)
                ?.getOrNull()

            if (forespoerselId == null) {
                "Ugyldig parameter: ${call.parameters["uuid"]}".let {
                    logger.warn(it)
                    respondBadRequest(it, String.serializer())
                }
            } else {
                logger.info("Henter data for forespørselId: $forespoerselId")
                val requestTimer = requestLatency.startTimer()
                measureTimeMillis {
                    try {
                        measureTimeMillis {
                            authorize(
                                forespoerselId = forespoerselId,
                                tilgangProducer = tilgangProducer,
                                redisPoller = redis,
                                cache = tilgangCache
                            )
                        }.also {
                            logger.info("Authorize took $it")
                        }

                        val clientId = kvitteringProducer.publish(forespoerselId)
                        var resultat: String?
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
                                val innsending = tilKvitteringResponse(Jackson.fromJson<InntektsmeldingDokument>(resultat!!))

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
                    } catch (e: JsonMappingException) {
                        "Kunne ikke parse json-resultat for forespørselId: $forespoerselId".let {
                            logger.error(it)
                            sikkerLogger.error(it, e)
                            respondInternalServerError(JacksonErrorResponse(forespoerselId.toString()), JacksonErrorResponse.serializer())
                        }
                    } catch (_: RedisPollerTimeoutException) {
                        logger.error("Fikk timeout for forespørselId: $forespoerselId")
                        respondInternalServerError(RedisTimeoutResponse(forespoerselId), RedisTimeoutResponse.serializer())
                    }
                }.also {
                    requestTimer.observeDuration()
                    logger.info("api call took $it")
                }
            }
        }
    }
}

private fun tilKvitteringResponse(inntektsmeldingDokument: InntektsmeldingDokument): KvitteringResponse =
    KvitteringResponse(
        orgnrUnderenhet = inntektsmeldingDokument.orgnrUnderenhet,
        identitetsnummer = inntektsmeldingDokument.identitetsnummer,
        fulltNavn = inntektsmeldingDokument.fulltNavn,
        virksomhetNavn = inntektsmeldingDokument.virksomhetNavn,
        behandlingsdager = inntektsmeldingDokument.behandlingsdager,
        egenmeldingsperioder = inntektsmeldingDokument.egenmeldingsperioder,
        arbeidsgiverperioder = inntektsmeldingDokument.arbeidsgiverperioder,
        bestemmendeFraværsdag = inntektsmeldingDokument.bestemmendeFraværsdag,
        fraværsperioder = inntektsmeldingDokument.fraværsperioder,
        inntekt = Inntekt(
            bekreftet = true,
            // Kan slette nullable inntekt og fallback når IM med gammelt format slettes fra database
            beregnetInntekt = inntektsmeldingDokument.inntekt?.beregnetInntekt ?: inntektsmeldingDokument.beregnetInntekt,
            endringÅrsak = inntektsmeldingDokument.inntekt?.endringÅrsak,
            manueltKorrigert = inntektsmeldingDokument.inntekt?.manueltKorrigert.orDefault(false)
        ),
        fullLønnIArbeidsgiverPerioden = inntektsmeldingDokument.fullLønnIArbeidsgiverPerioden,
        refusjon = inntektsmeldingDokument.refusjon,
        naturalytelser = inntektsmeldingDokument.naturalytelser,
        årsakInnsending = inntektsmeldingDokument.årsakInnsending,
        bekreftOpplysninger = true,
        tidspunkt = inntektsmeldingDokument.tidspunkt
    )
