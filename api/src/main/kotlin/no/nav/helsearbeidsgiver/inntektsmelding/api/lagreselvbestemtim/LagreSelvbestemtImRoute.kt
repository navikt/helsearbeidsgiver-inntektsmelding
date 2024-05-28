package no.nav.helsearbeidsgiver.inntektsmelding.api.lagreselvbestemtim

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
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.ResultJson
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
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.UkjentErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.util.UUID

// TODO test
fun Route.lagreSelvbestemtImRoute(
    rapid: RapidsConnection,
    tilgangskontroll: Tilgangskontroll,
    redisPoller: RedisPoller
) {
    val producer = LagreSelvbestemtImProducer(rapid)

    post(Routes.SELVBESTEMT_INNTEKTMELDING_MED_VALGFRI_ID) {
        val selvbestemtIdFraPath = call.parameters["selvbestemtId"]
            ?.runCatching(UUID::fromString)
            ?.getOrNull()

        val selvbestemtId: UUID = selvbestemtIdFraPath.orDefault(UUID.randomUUID())

        MdcUtils.withLogFields(
            Log.apiRoute(Routes.SELVBESTEMT_INNTEKTMELDING_MED_VALGFRI_ID),
            Log.selvbestemtId(selvbestemtId)
        ) {
            val skjema = lesRequestOrNull()
            when {
                skjema == null -> {
                    respondBadRequest(JsonErrorResponse(inntektsmeldingTypeId = selvbestemtId), JsonErrorResponse.serializer())
                }

                !skjema.erGyldig() -> {
                    "Fikk valideringsfeil.".also {
                        logger.info(it)
                        sikkerLogger.info(it)
                    }

                    // TODO returner (og logg) mer utfyllende feil
                    respondBadRequest("Valideringsfeil. Mer utfyllende feil må implementeres.", String.serializer())
                }

                (skjema.aarsakInnsending == AarsakInnsending.Ny && selvbestemtIdFraPath != null) ||
                    (skjema.aarsakInnsending == AarsakInnsending.Endring && selvbestemtIdFraPath == null) -> {
                    // TODO returner (og logg) mer utfyllende feil
                    respondBadRequest("Valideringsfeil pga. stiparameter. Mer utfyllende feil må implementeres.", String.serializer())
                }

                else -> {
                    tilgangskontroll.validerTilgangTilOrg(call.request, skjema.avsender.orgnr)

                    val avsenderFnr = call.request.lesFnrFraAuthToken()

                    val clientId = producer.publish(selvbestemtId, avsenderFnr, skjema)

                    MdcUtils.withLogFields(
                        Log.clientId(clientId)
                    ) {
                        runCatching {
                            redisPoller.hent(clientId)
                        }
                            .let {
                                sendResponse(selvbestemtId, it)
                            }
                    }
                }
            }
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.lesRequestOrNull(): SkjemaInntektsmelding? =
    call.receiveText()
        .parseJson()
        .also { json ->
            "Mottok selvbestemt inntektsmelding.".let {
                logger.info(it)
                sikkerLogger.info("$it:\n${json.toPretty()}")
            }
        }
        .runCatching {
            fromJson(SkjemaInntektsmelding.serializer())
        }
        .onFailure { e ->
            "Kunne ikke parse json.".let {
                logger.error(it)
                sikkerLogger.error(it, e)
            }
        }
        .getOrNull()

private suspend fun PipelineContext<Unit, ApplicationCall>.sendResponse(selvbestemtId: UUID, result: Result<JsonElement>) {
    result
        .map {
            it.fromJson(ResultJson.serializer())
        }
        .onSuccess {
            val success = it.success
            if (success != null) {
                logger.info("Selvbestemt inntektsmelding mottatt OK.")
                sikkerLogger.info("Selvbestemt inntektsmelding mottatt OK:\n${success.toPretty()}")
                respond(HttpStatusCode.OK, LagreSelvbestemtImResponse(selvbestemtId), LagreSelvbestemtImResponse.serializer())
            } else {
                val feilmelding = it.failure?.fromJson(String.serializer()).orDefault("Tomt resultat i Redis.")

                logger.info("Fikk feil under mottagelse av selvbestemt inntektsmelding.")
                sikkerLogger.info("Fikk feil under mottagelse av selvbestemt inntektsmelding: $feilmelding")
                respondInternalServerError(UkjentErrorResponse(selvbestemtId), UkjentErrorResponse.serializer())
            }
        }
        .onFailure {
            logger.info("Klarte ikke hente resultat.")
            sikkerLogger.info("Klarte ikke hente resultat.", it)
            when (it) {
                is RedisPollerTimeoutException ->
                    respondInternalServerError(RedisTimeoutResponse(inntektsmeldingTypeId = selvbestemtId), RedisTimeoutResponse.serializer())

                else ->
                    respondInternalServerError(RedisPermanentErrorResponse(selvbestemtId), RedisPermanentErrorResponse.serializer())
            }
        }
}

// TODO
private fun SkjemaInntektsmelding.erGyldig(): Boolean =
    true
