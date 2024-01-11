package no.nav.helsearbeidsgiver.inntektsmelding.api.aapeninntektmelding

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.lesFnrFraAuthToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.JsonErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisPermanentErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.util.UUID

// TODO test
fun Route.aapenInntektmeldingRoute(
    rapid: RapidsConnection,
    tilgangskontroll: Tilgangskontroll,
    redisPoller: RedisPoller
) {
    val producer = AapenInntektmeldingProducer(rapid)

    post(Routes.AAPEN_INNTEKTMELDING) {
        val aapenId: UUID = UUID.randomUUID()

        MdcUtils.withLogFields(
            Log.apiRoute(Routes.AAPEN_INNTEKTMELDING),
            Log.aapenId(aapenId)
        ) {
            val skjema = lesRequestOrNull(aapenId)
            if (skjema != null) {
                tilgangskontroll.validerTilgangTilOrg(call.request, aapenId, skjema.avsender.orgnr)

                val avsenderFnr = call.request.lesFnrFraAuthToken()

                val clientId = producer.publish(aapenId, avsenderFnr, skjema)

                MdcUtils.withLogFields(
                    Log.clientId(clientId)
                ) {
                    runCatching {
                        redisPoller.hent(clientId)
                    }
                        .let {
                            sendResponse(aapenId, it)
                        }
                }
            }
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.lesRequestOrNull(aapenId: UUID): SkjemaInntektsmelding? =
    call.receiveText()
        .parseJson()
        .also { json ->
            "Mottok åpen inntektsmelding.".let {
                logger.info(it)
                sikkerLogger.info("$it:\n${json.toPretty()}")
            }
        }
        .runCatching {
            fromJson(SkjemaInntektsmelding.serializer())
        }
        .getOrElse { e ->
            "Kunne ikke parse json.".let {
                logger.error(it)
                sikkerLogger.error(it, e)
                respondBadRequest(JsonErrorResponse(inntektsmeldingId = aapenId), JsonErrorResponse.serializer())
                null
            }
        }
        ?.let {
            if (it.erGyldig()) {
                it
            } else {
                logger.info("Fikk valideringsfeil.")

                // TODO returner mer utfyllende feil
                respondBadRequest("Valideringsfeil. Mer utfyllende feil må implementeres.", String.serializer())
                null
            }
        }

private suspend fun PipelineContext<Unit, ApplicationCall>.sendResponse(aapenId: UUID, result: Result<JsonElement>) {
    result
        .onSuccess {
            logger.info("Åpen inntektsmelding vellykket mottatt.")
            sikkerLogger.info("Åpen inntektsmelding vellykket mottatt:\n${it.toPretty()}")
            respond(HttpStatusCode.OK, AapenInntektmeldingResponse(aapenId), AapenInntektmeldingResponse.serializer())
        }
        .onFailure {
            logger.info("Klarte ikke hente resultat.")
            sikkerLogger.info("Klarte ikke hente resultat.", it)
            when (it) {
                is RedisPollerTimeoutException ->
                    respondInternalServerError(RedisTimeoutResponse(inntektsmeldingId = aapenId), RedisTimeoutResponse.serializer())

                else ->
                    respondInternalServerError(RedisPermanentErrorResponse(aapenId), RedisPermanentErrorResponse.serializer())
            }
        }
}

// TODO
private fun SkjemaInntektsmelding.erGyldig(): Boolean =
    true
