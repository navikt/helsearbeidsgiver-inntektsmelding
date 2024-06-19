package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoersel

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.prometheus.client.Summary
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.HentForespoerselResultat
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerJsonParseException
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.lesFnrFraAuthToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisPermanentErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.receive
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondForbidden
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationError
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson

fun Route.hentForespoerselRoute(
    rapid: RapidsConnection,
    tilgangskontroll: Tilgangskontroll,
    redisPoller: RedisPoller
) {
    val hentForespoerselProducer = HentForespoerselProducer(rapid)
    val requestLatency = Summary.build()
        .name("simba_hent_forespoersel_latency_seconds")
        .help("hent forespoersel endpoint latency in seconds")
        .register()

    setOf(Routes.TRENGER, Routes.HENT_FORESPOERSEL).forEach { routeUrl ->
        post(routeUrl) {
            val requestTimer = requestLatency.startTimer()
            runCatching {
                receive(HentForespoerselRequest.serializer())
            }
                .onSuccess { request ->
                    logger.info("Henter data for uuid: ${request.uuid}")
                    try {
                        tilgangskontroll.validerTilgangTilForespoersel(call.request, request.uuid)

                        val arbeidsgiverFnr = call.request.lesFnrFraAuthToken()

                        val transaksjonId = hentForespoerselProducer.publish(request, arbeidsgiverFnr)

                        val resultatJson = redisPoller.hent(transaksjonId).fromJson(ResultJson.serializer())

                        sikkerLogger.info("Hentet forespørsel: $resultatJson")

                        val resultat = resultatJson.success?.fromJson(HentForespoerselResultat.serializer())
                        if (resultat != null) {
                            respond(HttpStatusCode.Created, resultat.toResponse(), HentForespoerselResponse.serializer())
                        } else {
                            val feilmelding = resultatJson.failure?.fromJson(String.serializer()) ?: "Teknisk feil, prøv igjen senere."
                            val response = ResultJson(
                                failure = feilmelding.toJson()
                            )
                            respond(HttpStatusCode.ServiceUnavailable, response, ResultJson.serializer())
                        }
                    } catch (e: ManglerAltinnRettigheterException) {
                        val response = ResultJson(
                            failure = "Du har ikke rettigheter for organisasjon.".toJson()
                        )
                        respondForbidden(response, ResultJson.serializer())
                    } catch (_: RedisPollerJsonParseException) {
                        logger.info("Fikk parsefeil for ${request.uuid}")
                        val response = ResultJson(
                            failure = RedisPermanentErrorResponse(request.uuid).toJson(RedisPermanentErrorResponse.serializer())
                        )
                        respondInternalServerError(response, ResultJson.serializer())
                    } catch (_: RedisPollerTimeoutException) {
                        logger.info("Fikk timeout for ${request.uuid}")
                        val response = ResultJson(
                            failure = RedisTimeoutResponse(request.uuid).toJson(RedisTimeoutResponse.serializer())
                        )
                        respondInternalServerError(response, ResultJson.serializer())
                    }
                }.also {
                    requestTimer.observeDuration()
                }
                .onFailure {
                    logger.error("Klarte ikke lese request.", it)
                    val response = ResultJson(
                        failure = ValidationResponse(
                            listOf(
                                ValidationError(
                                    property = HentForespoerselRequest::uuid.name,
                                    error = it.message.orEmpty(),
                                    value = "<ukjent>"
                                )
                            )
                        )
                            .toJson(ValidationResponse.serializer())
                    )
                    respondBadRequest(response, ResultJson.serializer())
                }
        }
    }
}

private fun HentForespoerselResultat.toResponse(): HentForespoerselResponse {
    val response = HentForespoerselResponse(
        navn = sykmeldtNavn,
        innsenderNavn = avsenderNavn,
        orgNavn = orgNavn,
        identitetsnummer = forespoersel.fnr,
        orgnrUnderenhet = forespoersel.orgnr,
        skjaeringstidspunkt = forespoersel.eksternBestemmendeFravaersdag(),
        fravaersperioder = forespoersel.sykmeldingsperioder,
        egenmeldingsperioder = forespoersel.egenmeldingsperioder,
        bestemmendeFravaersdag = forespoersel.forslagBestemmendeFravaersdag(),
        eksternBestemmendeFravaersdag = forespoersel.eksternBestemmendeFravaersdag(),
        bruttoinntekt = inntekt?.gjennomsnitt(),
        tidligereinntekter = inntekt?.maanedOversikt.orEmpty(),
        behandlingsperiode = null,
        behandlingsdager = emptyList(),
        forespurtData = forespoersel.forespurtData,
        erBesvart = forespoersel.erBesvart,
        feilReport = if (feil.isEmpty()) {
            null
        } else {
            FeilReport(
                feil = feil.map {
                    Feilmelding(
                        melding = it.value,
                        status = null,
                        datafelt = it.key
                    )
                }.toMutableList()
            )
        }
    )

    return response.copy(
        success = response.toJson(HentForespoerselResponse.serializer())
    )
}
