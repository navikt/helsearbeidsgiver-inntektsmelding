package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.prometheus.client.Summary
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Kvittering
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.KvitteringEkstern
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.KvitteringSimba
import no.nav.helsearbeidsgiver.felles.InnsendtInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.JsonErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.fjernLedendeSlash
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondForbidden
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondNotFound
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.time.ZoneId
import java.util.UUID
import kotlin.system.measureTimeMillis

private const val EMPTY_PAYLOAD = "{}"

fun Route.kvitteringRoute(
    rapid: RapidsConnection,
    tilgangskontroll: Tilgangskontroll,
    redisPoller: RedisPoller
) {
    val kvitteringProducer = KvitteringProducer(rapid)

    val requestLatency = Summary.build()
        .name("simba_kvittering_latency_seconds")
        .help("kvittering endpoint latency in seconds")
        .register()

    route(Routes.KVITTERING) {
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
                            tilgangskontroll.validerTilgangTilForespoersel(call.request, forespoerselId)
                        }.also {
                            logger.info("Authorize took $it")
                        }

                        val clientId = kvitteringProducer.publish(forespoerselId)
                        var resultat: String?
                        measureTimeMillis {
                            resultat = redisPoller.getString(clientId, 10, 500)
                        }.also {
                            logger.info("redis polling took $it")
                        }
                        sikkerLogger.info("Forespørsel $forespoerselId ga resultat: $resultat")

                        if (resultat == EMPTY_PAYLOAD) {
                            // kvitteringService svarer med "{}" hvis det ikke er noen kvittering
                            respondNotFound("Kvittering ikke funnet for forespørselId: $forespoerselId", String.serializer())
                        } else {
                            val innsendtInntektsmelding = resultat!!.fromJson(InnsendtInntektsmelding.serializer())

                            if (innsendtInntektsmelding.dokument == null && innsendtInntektsmelding.eksternInntektsmelding == null) {
                                respondNotFound("Kvittering ikke funnet for forespørselId: $forespoerselId", String.serializer())
                            }
                            measureTimeMillis {
                                val innsending = tilKvittering(innsendtInntektsmelding)
                                respondOk(
                                    innsending.toJson(Kvittering.serializer()),
                                    JsonElement.serializer()
                                )
                            }.also {
                                logger.info("Mapping og respond took $it")
                            }
                        }
                    } catch (e: ManglerAltinnRettigheterException) {
                        respondForbidden("Du har ikke rettigheter for organisasjon.", String.serializer())
                    } catch (e: SerializationException) {
                        "Kunne ikke parse json-resultat for forespørselId: $forespoerselId".let {
                            logger.error(it)
                            sikkerLogger.error(it, e)
                            respondInternalServerError(JsonErrorResponse(forespoerselId.toString()), JsonErrorResponse.serializer())
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

private fun tilKvittering(innsendtInntektsmelding: InnsendtInntektsmelding): Kvittering =
    Kvittering(
        kvitteringDokument = innsendtInntektsmelding.dokument?.let { inntektsmeldingDokument ->
            KvitteringSimba(
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
                tidspunkt = inntektsmeldingDokument.tidspunkt,
                forespurtData = inntektsmeldingDokument.forespurtData,
                telefonnummer = inntektsmeldingDokument.telefonnummer,
                innsenderNavn = inntektsmeldingDokument.innsenderNavn
            )
        },
        kvitteringEkstern = innsendtInntektsmelding.eksternInntektsmelding?.let { eIm ->
            KvitteringEkstern(
                eIm.avsenderSystemNavn,
                eIm.arkivreferanse,
                eIm.tidspunkt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
            )
        }
    )
