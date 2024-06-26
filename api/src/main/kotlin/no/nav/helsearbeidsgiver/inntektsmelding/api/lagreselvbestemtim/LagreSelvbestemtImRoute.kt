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
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.lesFnrFraAuthToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ArbeidsforholdErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.JsonErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisPermanentErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.UkjentErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ValideringErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
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

    post(Routes.SELVBESTEMT_INNTEKTSMELDING) {
        val clientId = UUID.randomUUID()

        MdcUtils.withLogFields(
            Log.apiRoute(Routes.SELVBESTEMT_INNTEKTSMELDING),
            Log.clientId(clientId)
        ) {
            val skjema = lesRequestOrNull()
            when {
                skjema == null -> {
                    respondBadRequest(JsonErrorResponse(), JsonErrorResponse.serializer())
                }

                skjema.valider().isNotEmpty() -> {
                    val valideringsfeil = skjema.valider()

                    "Fikk valideringsfeil: $valideringsfeil".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }

                    val response = ValideringErrorResponse(valideringsfeil)

                    respondBadRequest(response, ValideringErrorResponse.serializer())
                }

                else -> {
                    tilgangskontroll.validerTilgangTilOrg(call.request, skjema.avsender.orgnr.verdi)

                    val avsenderFnr = call.request.lesFnrFraAuthToken()

                    producer.publish(clientId, skjema, avsenderFnr)

                    runCatching {
                        redisPoller.hent(clientId)
                    }
                        .let {
                            sendResponse(it)
                        }
                }
            }
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.lesRequestOrNull(): SkjemaInntektsmeldingSelvbestemt? =
    call.receiveText()
        .parseJson()
        .also { json ->
            "Mottok selvbestemt inntektsmelding.".let {
                logger.info(it)
                sikkerLogger.info("$it:\n${json.toPretty()}")
            }
        }
        .runCatching {
            fromJson(SkjemaInntektsmeldingSelvbestemt.serializer())
        }
        .onFailure { e ->
            "Kunne ikke parse json.".let {
                logger.error(it)
                sikkerLogger.error(it, e)
            }
        }
        .getOrNull()

private suspend fun PipelineContext<Unit, ApplicationCall>.sendResponse(resultatJson: Result<JsonElement>) {
    resultatJson
        .map {
            it.fromJson(ResultJson.serializer())
        }
        .onSuccess { resultat ->
            val selvbestemtId = resultat.success?.fromJson(UuidSerializer)
            if (selvbestemtId != null) {
                MdcUtils.withLogFields(
                    Log.selvbestemtId(selvbestemtId)
                ) {
                    "Selvbestemt inntektsmelding mottatt OK for ID '$selvbestemtId'.".also {
                        logger.info(it)
                        sikkerLogger.info(it)
                    }
                }
                respond(HttpStatusCode.OK, LagreSelvbestemtImResponse(selvbestemtId), LagreSelvbestemtImResponse.serializer())
            } else {
                val feilmelding = resultat.failure?.fromJson(String.serializer()).orDefault("Tomt resultat i Redis.")
                logger.info("Fikk feil under mottagelse av selvbestemt inntektsmelding.")
                sikkerLogger.info("Fikk feil under mottagelse av selvbestemt inntektsmelding: $feilmelding")
                if ("Mangler arbeidsforhold i perioden" == feilmelding) {
                    respondBadRequest(ArbeidsforholdErrorResponse(), ArbeidsforholdErrorResponse.serializer())
                } else {
                    respondInternalServerError(UkjentErrorResponse(), UkjentErrorResponse.serializer())
                }
            }
        }
        .onFailure {
            logger.info("Klarte ikke hente resultat.")
            sikkerLogger.info("Klarte ikke hente resultat.", it)
            when (it) {
                is RedisPollerTimeoutException ->
                    respondInternalServerError(RedisTimeoutResponse(), RedisTimeoutResponse.serializer())

                else ->
                    respondInternalServerError(RedisPermanentErrorResponse(), RedisPermanentErrorResponse.serializer())
            }
        }
}
