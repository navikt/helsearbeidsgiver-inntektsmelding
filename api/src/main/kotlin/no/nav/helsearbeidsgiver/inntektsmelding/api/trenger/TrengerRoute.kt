package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.prometheus.client.Summary
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TrengerData
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.authorize
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.hentIdentitetsnummerFraLoginToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.receive
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationError
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.helsearbeidsgiver.utils.json.fromJson

fun Route.trengerRoute(
    rapid: RapidsConnection,
    redis: RedisPoller,
    tilgangCache: LocalCache<Tilgang>
) {
    val trengerProducer = TrengerProducer(rapid)
    val tilgangProducer = TilgangProducer(rapid)
    val requestLatency = Summary.build()
        .name("simba_trenger_latency_seconds")
        .help("trenger endpoint latency in seconds")
        .register()

    route(Routes.TRENGER) {
        post {
            val requestTimer = requestLatency.startTimer()
            runCatching {
                call.receive<TrengerRequest>()
            }
                .onSuccess { request ->
                    logger.info("Henter data for uuid: ${request.uuid}")
                    try {
                        authorize(
                            forespoerselId = request.uuid,
                            tilgangProducer = tilgangProducer,
                            redisPoller = redis,
                            cache = tilgangCache
                        )
                        val arbeidsgiverFnr = hentIdentitetsnummerFraLoginToken(application.environment.config, call.request)
                        val trengerId = trengerProducer.publish(request = request, arbeidsgiverFnr = arbeidsgiverFnr)
                        val resultat = redis.getString(trengerId, 10, 500)
                        sikkerLogger.info("Fikk resultat: $resultat")
                        val trengerResponse = mapTrengerResponse(resultat.fromJson(TrengerData.serializer()))
                        val status = if (trengerResponse.feilReport == null) {
                            HttpStatusCode.Created
                        } else if (trengerResponse.feilReport.status() < 0) HttpStatusCode.ServiceUnavailable else HttpStatusCode.Created
                        call.respond(status, trengerResponse)
                    } catch (e: ManglerAltinnRettigheterException) {
                        call.respond(HttpStatusCode.Forbidden, "Du har ikke rettigheter for organisasjon.")
                    } catch (_: RedisPollerTimeoutException) {
                        logger.info("Fikk timeout for ${request.uuid}")
                        call.respond(HttpStatusCode.InternalServerError, RedisTimeoutResponse(request.uuid))
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
                    call.respond(HttpStatusCode.BadRequest, response)
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
        fravaersperioder = trengerData.fravarsPerioder ?: emptyList(),
        egenmeldingsperioder = trengerData.egenmeldingsPerioder ?: emptyList(),
        bruttoinntekt = trengerData.bruttoinntekt,
        tidligereinntekter = trengerData.tidligereinntekter ?: emptyList(),
        behandlingsperiode = null,
        behandlingsdager = emptyList(),
        forespurtData = trengerData.forespurtData,
        feilReport = trengerData.feilReport
    )
}
