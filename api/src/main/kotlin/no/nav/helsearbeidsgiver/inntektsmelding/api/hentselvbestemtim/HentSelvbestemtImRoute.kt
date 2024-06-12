package no.nav.helsearbeidsgiver.inntektsmelding.api.hentselvbestemtim

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisPermanentErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.util.UUID

fun Route.hentSelvbestemtImRoute(
    rapid: RapidsConnection,
    tilgangskontroll: Tilgangskontroll,
    redisPoller: RedisPoller
) {
    val producer = HentSelvbestemtImProducer(rapid)

    get(Routes.SELVBESTEMT_INNTEKTSMELDING_MED_ID) {
        val selvbestemtId = call.parameters["selvbestemtId"]
            ?.runCatching(UUID::fromString)
            ?.getOrNull()

        if (selvbestemtId == null) {
            "Ugyldig parameter: '${call.parameters["selvbestemtId"]}'".let {
                logger.error(it)
                sikkerLogger.error(it)
                respondBadRequest(it, String.serializer())
            }
        } else {
            MdcUtils.withLogFields(
                Log.apiRoute(Routes.SELVBESTEMT_INNTEKTSMELDING_MED_ID),
                Log.selvbestemtId(selvbestemtId)
            ) {
                val transaksjonId = producer.publish(selvbestemtId)

                MdcUtils.withLogFields(
                    Log.transaksjonId(transaksjonId)
                ) {
                    runCatching {
                        redisPoller.hent(transaksjonId)
                    }
                        .onSuccess {
                            val result = it.fromJson(ResultJson.serializer())

                            val inntektsmelding = result.success?.fromJson(Inntektsmelding.serializer())

                            if (inntektsmelding != null) {
                                tilgangskontroll.validerTilgangTilOrg(call.request, inntektsmelding.avsender.orgnr)
                                sendOkResponse(inntektsmelding)
                            } else {
                                val feilmelding = result.failure
                                    ?.fromJson(String.serializer())
                                    .orDefault("Ukjent feil.")

                                sendErrorResponse(feilmelding)
                            }
                        }
                        .onFailure {
                            sendRedisErrorResponse(selvbestemtId, it)
                        }
                }
            }
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.sendOkResponse(inntektsmelding: Inntektsmelding) {
    "Selvbestemt inntektsmelding hentet OK.".also {
        logger.info(it)
        sikkerLogger.info("$it\n$inntektsmelding")
    }
    val response = ResultJson(
        success = HentSelvbestemtImResponseSuccess(inntektsmelding).toJson(HentSelvbestemtImResponseSuccess.serializer())
    )
    respondOk(response, ResultJson.serializer())
}

private suspend fun PipelineContext<Unit, ApplicationCall>.sendErrorResponse(feilmelding: String) {
    "Klarte ikke hente inntektsmelding pga. feil.".also {
        logger.info(it)
        sikkerLogger.info("$it Feilmelding: '$feilmelding'")
    }
    val response = ResultJson(
        failure = HentSelvbestemtImResponseFailure(feilmelding).toJson(HentSelvbestemtImResponseFailure.serializer())
    )
    respondInternalServerError(response, ResultJson.serializer())
}

private suspend fun PipelineContext<Unit, ApplicationCall>.sendRedisErrorResponse(selvbestemtId: UUID, error: Throwable) {
    "Klarte ikke hente inntektsmelding pga. feil i Redis.".also {
        logger.info(it)
        sikkerLogger.info(it, error)
    }
    when (error) {
        is RedisPollerTimeoutException -> {
            val response = ResultJson(
                failure = RedisTimeoutResponse(inntektsmeldingTypeId = selvbestemtId).toJson(RedisTimeoutResponse.serializer())
            )
            respondInternalServerError(response, ResultJson.serializer())
        }

        else -> {
            val response = ResultJson(
                failure = RedisPermanentErrorResponse(selvbestemtId).toJson(RedisPermanentErrorResponse.serializer())
            )
            respondInternalServerError(response, ResultJson.serializer())
        }
    }
}
