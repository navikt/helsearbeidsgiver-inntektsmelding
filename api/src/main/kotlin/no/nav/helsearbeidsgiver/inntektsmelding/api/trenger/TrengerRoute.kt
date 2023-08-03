package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.TrengerData
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.authorize
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.receive
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondForbidden
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationError
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import no.nav.helsearbeidsgiver.utils.json.fromJson

fun RouteExtra.trengerRoute() {
    val trengerProducer = TrengerProducer(connection)
    val tilgangProducer = TilgangProducer(connection)

    route.route(Routes.TRENGER) {
        post {
            runCatching {
                receive(TrengerRequest.serializer())
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

                        val trengerId = trengerProducer.publish(request)
                        val resultat = redis.getString(trengerId, 10, 500)
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
