package no.nav.helsearbeidsgiver.inntektsmelding.api.hentaapenim

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

// TODO test
fun Route.hentAapenImRoute(
    rapid: RapidsConnection,
    tilgangskontroll: Tilgangskontroll,
    redisPoller: RedisPoller
) {
    val producer = HentAapenImProducer(rapid)

    get(Routes.AAPEN_INNTEKTMELDING_MED_ID) {
        val aapenId = call.parameters["aapenId"]
            ?.runCatching(UUID::fromString)
            ?.getOrNull()

        if (aapenId == null) {
            "Ugyldig parameter: '${call.parameters["aapenId"]}'".let {
                logger.error(it)
                sikkerLogger.error(it)
                respondBadRequest(it, String.serializer())
            }
        } else {
            MdcUtils.withLogFields(
                Log.apiRoute(Routes.AAPEN_INNTEKTMELDING_MED_ID),
                Log.aapenId(aapenId)
            ) {
                val clientId = producer.publish(aapenId)

                MdcUtils.withLogFields(
                    Log.clientId(clientId)
                ) {
                    runCatching {
                        redisPoller.hent(clientId)
                    }
                        .onSuccess {
                            val result = it.fromJson(ResultJson.serializer())

                            val inntektsmelding = result.success?.fromJson(Inntektsmelding.serializer())

                            if (inntektsmelding != null) {
                                tilgangskontroll.validerTilgangTilOrg(call.request, aapenId, inntektsmelding.avsender.orgnr)
                                sendOkResponse(inntektsmelding)
                            } else {
                                val feilmelding = result.failure
                                    ?.fromJson(String.serializer())
                                    .orDefault("Ukjent feil.")

                                sendErrorResponse(feilmelding)
                            }
                        }
                        .onFailure {
                            sendRedisErrorResponse(aapenId, it)
                        }
                }
            }
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.sendOkResponse(inntektsmelding: Inntektsmelding) {
    "Åpen inntektsmelding hentet OK.".also {
        logger.info(it)
        sikkerLogger.info("$it\n$inntektsmelding")
    }
    val response = ResultJson(
        success = HentAapenImResponseSuccess(inntektsmelding).toJson(HentAapenImResponseSuccess.serializer())
    )
    respondOk(response, ResultJson.serializer())
}

private suspend fun PipelineContext<Unit, ApplicationCall>.sendErrorResponse(feilmelding: String) {
    "Klarte ikke hente inntektsmelding pga. feil.".also {
        logger.info(it)
        sikkerLogger.info("$it Feilmelding: '$feilmelding'")
    }
    val response = ResultJson(
        failure = HentAapenImResponseFailure(feilmelding).toJson(HentAapenImResponseFailure.serializer())
    )
    respondInternalServerError(response, ResultJson.serializer())
}

private suspend fun PipelineContext<Unit, ApplicationCall>.sendRedisErrorResponse(aapenId: UUID, error: Throwable) {
    "Klarte ikke hente inntektsmelding pga. feil i Redis.".also {
        logger.info(it)
        sikkerLogger.info(it, error)
    }
    when (error) {
        is RedisPollerTimeoutException -> {
            val response = ResultJson(
                failure = RedisTimeoutResponse(inntektsmeldingId = aapenId).toJson(RedisTimeoutResponse.serializer())
            )
            respondInternalServerError(response, ResultJson.serializer())
        }

        else -> {
            val response = ResultJson(
                failure = RedisPermanentErrorResponse(aapenId).toJson(RedisPermanentErrorResponse.serializer())
            )
            respondInternalServerError(response, ResultJson.serializer())
        }
    }
}
