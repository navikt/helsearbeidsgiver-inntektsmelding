package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.prometheus.client.Summary
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.TrengerData
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
import java.time.LocalDate

fun Route.trengerRoute(
    rapid: RapidsConnection,
    tilgangskontroll: Tilgangskontroll,
    redisPoller: RedisPoller
) {
    val trengerProducer = TrengerProducer(rapid)
    val requestLatency = Summary.build()
        .name("simba_trenger_latency_seconds")
        .help("trenger endpoint latency in seconds")
        .register()

    route(Routes.TRENGER) {
        post {
            val requestTimer = requestLatency.startTimer()
            runCatching {
                receive(TrengerRequest.serializer())
            }
                .onSuccess { request ->
                    logger.info("Henter data for uuid: ${request.uuid}")
                    try {
                        tilgangskontroll.validerTilgangTilForespoersel(call.request, request.uuid)

                        val arbeidsgiverFnr = call.request.lesFnrFraAuthToken()

                        val clientId = trengerProducer.publish(request, arbeidsgiverFnr)

                        val resultat = redisPoller.hent(clientId).fromJson(ResultJson.serializer())

                        sikkerLogger.info("Fikk resultat: $resultat")

                        val data = resultat.success?.fromJson(TrengerData.serializer())
                        val trengerResponse = if (data != null) {
                            mapTrengerResponse(data)
                        } else {
                            val feilmelding = resultat.failure?.fromJson(String.serializer()) ?: "Teknisk feil, prøv igjen senere."
                            feilTrengerResponse(feilmelding)
                        }

                        val status = if (trengerResponse.feilReport != null && trengerResponse.feilReport.status() < 0) {
                            HttpStatusCode.ServiceUnavailable
                        } else {
                            HttpStatusCode.Created
                        }

                        respond(status, trengerResponse, TrengerResponse.serializer())
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
                                    property = TrengerRequest::uuid.name,
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

private fun mapTrengerResponse(trengerData: TrengerData): TrengerResponse {
    val response = TrengerResponse(
        navn = trengerData.personDato?.navn ?: "",
        innsenderNavn = trengerData.arbeidsgiver?.navn ?: "",
        orgNavn = trengerData.virksomhetNavn ?: "",
        identitetsnummer = trengerData.fnr ?: "",
        orgnrUnderenhet = trengerData.orgnr ?: "",
        skjaeringstidspunkt = trengerData.skjaeringstidspunkt,
        fravaersperioder = trengerData.fravarsPerioder ?: emptyList(),
        egenmeldingsperioder = trengerData.egenmeldingsPerioder ?: emptyList(),
        bestemmendeFravaersdag = trengerData.forespoersel.forslagBestemmendeFravaersdag(),
        eksternBestemmendeFravaersdag = trengerData.forespoersel.eksternBestemmendeFravaersdag(),
        bruttoinntekt = trengerData.bruttoinntekt,
        tidligereinntekter = trengerData.tidligereinntekter ?: emptyList(),
        behandlingsperiode = null,
        behandlingsdager = emptyList(),
        forespurtData = trengerData.forespurtData,
        feilReport = trengerData.feilReport
    )

    return response.copy(
        success = response.toJson(TrengerResponse.serializer())
    )
}

private fun feilTrengerResponse(feilmelding: String): TrengerResponse {
    val response = TrengerResponse(
        navn = "",
        innsenderNavn = "",
        orgNavn = "",
        identitetsnummer = "",
        orgnrUnderenhet = "",
        skjaeringstidspunkt = null,
        fravaersperioder = emptyList(),
        egenmeldingsperioder = emptyList(),
        bestemmendeFravaersdag = LocalDate.of(0, 1, 1),
        eksternBestemmendeFravaersdag = null,
        bruttoinntekt = null,
        tidligereinntekter = emptyList(),
        behandlingsperiode = null,
        behandlingsdager = emptyList(),
        forespurtData = null,
        feilReport = FeilReport(
            mutableListOf(
                Feilmelding(feilmelding, -1, datafelt = Key.FORESPOERSEL_SVAR)
            )
        )
    )

    return response.copy(
        failure = response.toJson(TrengerResponse.serializer())
    )
}
