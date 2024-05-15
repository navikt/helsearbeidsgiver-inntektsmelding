package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.prometheus.client.Summary
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.TrengerData
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.lesFnrFraAuthToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
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

                        val trengerId = trengerProducer.publish(request, arbeidsgiverFnr)
                        val resultat = redisPoller.getString(trengerId, 10, 500)

                        sikkerLogger.info("Fikk resultat: $resultat")

                        val trengerResponse = mapTrengerResponse(resultat.fromJson(TrengerData.serializer()))

                        val status = if (trengerResponse.feilReport == null) {
                            HttpStatusCode.Created
                        } else if (trengerResponse.feilReport.status() < 0) HttpStatusCode.ServiceUnavailable else HttpStatusCode.Created

                        respond(status, trengerResponse, TrengerResponse.serializer())
                    } catch (e: ManglerAltinnRettigheterException) {
                        respondForbidden("Du har ikke rettigheter for organisasjon.", String.serializer())
                    } catch (_: RedisPollerTimeoutException) {
                        logger.info("Fikk timeout for ${request.uuid}")
                        respondInternalServerError(RedisTimeoutResponse(request.uuid), RedisTimeoutResponse.serializer())
                    }
                }.also {
                    requestTimer.observeDuration()
                }
                .onFailure {
                    logger.error("Klarte ikke lese request.", it)
                    val response = ValidationResponse(
                        listOf(
                            ValidationError(
                                property = TrengerRequest::uuid.name,
                                error = it.message.orEmpty(),
                                value = "<ukjent>"
                            )
                        )
                    )
                    respondBadRequest(response, ValidationResponse.serializer())
                }
        }
    }
}

fun mapTrengerResponse(trengerData: TrengerData): TrengerResponse {
    return TrengerResponse(
        navn = trengerData.personDato?.navn ?: "",
        innsenderNavn = trengerData.arbeidsgiver?.navn ?: "",
        orgNavn = trengerData.virksomhetNavn ?: "",
        identitetsnummer = trengerData.fnr ?: "",
        orgnrUnderenhet = trengerData.orgnr ?: "",
        skjaeringstidspunkt = trengerData.skjaeringstidspunkt,
        fravaersperioder = trengerData.fravarsPerioder ?: emptyList(),
        egenmeldingsperioder = trengerData.egenmeldingsPerioder ?: emptyList(),
        // TODO fjern !! når forespoersel ikke lenger er nullable
        bestemmendeFravaersdag = trengerData.forespoersel!!.forslagBestemmendeFravaersdag(),
        eksternBestemmendeFravaersdag = trengerData.forespoersel?.eksternBestemmendeFravaersdag(),
        bruttoinntekt = trengerData.bruttoinntekt,
        tidligereinntekter = trengerData.tidligereinntekter ?: emptyList(),
        behandlingsperiode = null,
        behandlingsdager = emptyList(),
        forespurtData = trengerData.forespurtData,
        feilReport = trengerData.feilReport
    )
}
