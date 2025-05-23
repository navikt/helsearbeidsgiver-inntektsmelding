package no.nav.helsearbeidsgiver.inntektsmelding.api.hentselvbestemtim

import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.kafka.Producer
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
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
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.util.UUID

fun Route.hentSelvbestemtImRoute(
    producer: Producer,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val redisPoller = RedisStore(redisConnection, RedisPrefix.HentSelvbestemtIm).let(::RedisPoller)

    get(Routes.SELVBESTEMT_INNTEKTSMELDING_MED_ID) {
        val kontekstId = UUID.randomUUID()

        val selvbestemtId =
            call.parameters["selvbestemtId"]
                ?.runCatching(UUID::fromString)
                ?.getOrNull()

        if (selvbestemtId == null) {
            "Ugyldig parameter: '${call.parameters["selvbestemtId"]}'.".let {
                logger.error(it)
                sikkerLogger.error(it)
                respondBadRequest(it, String.serializer())
            }
        } else {
            MdcUtils.withLogFields(
                Log.apiRoute(Routes.SELVBESTEMT_INNTEKTSMELDING_MED_ID),
                Log.kontekstId(kontekstId),
                Log.selvbestemtId(selvbestemtId),
            ) {
                producer.sendRequestEvent(kontekstId, selvbestemtId)

                runCatching {
                    redisPoller.hent(kontekstId)
                }.onSuccess { result ->
                    val inntektsmelding = result.success?.fromJson(Inntektsmelding.serializer())

                    if (inntektsmelding != null) {
                        tilgangskontroll.validerTilgangTilOrg(call.request, inntektsmelding.avsender.orgnr)
                        sendOkResponse(inntektsmelding)
                    } else {
                        val feilmelding =
                            result.failure
                                ?.fromJson(String.serializer())
                                .orDefault("Ukjent feil.")

                        sendErrorResponse(feilmelding)
                    }
                }.onFailure {
                    sendRedisErrorResponse(selvbestemtId, it)
                }
            }
        }
    }
}

private fun Producer.sendRequestEvent(
    kontekstId: UUID,
    selvbestemtId: UUID,
) {
    send(
        key = selvbestemtId,
        message =
            mapOf(
                Key.EVENT_NAME to EventName.SELVBESTEMT_IM_REQUESTED.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.SELVBESTEMT_ID to selvbestemtId.toJson(),
                    ).toJson(),
            ),
    )
}

private suspend fun RoutingContext.sendOkResponse(inntektsmelding: Inntektsmelding) {
    val response = HentSelvbestemtImResponseSuccess(inntektsmelding).toJson(HentSelvbestemtImResponseSuccess.serializer())

    "Selvbestemt inntektsmelding hentet OK.".also {
        logger.info(it)
        sikkerLogger.info("$it\n${response.toPretty()}")
    }

    respondOk(ResultJson(success = response), ResultJson.serializer())
}

private suspend fun RoutingContext.sendErrorResponse(feilmelding: String) {
    "Klarte ikke hente inntektsmelding pga. feil.".also {
        logger.error(it)
        sikkerLogger.error("$it Feilmelding: '$feilmelding'")
    }
    val response =
        ResultJson(
            failure = HentSelvbestemtImResponseFailure(feilmelding).toJson(HentSelvbestemtImResponseFailure.serializer()),
        )
    respondInternalServerError(response, ResultJson.serializer())
}

private suspend fun RoutingContext.sendRedisErrorResponse(
    selvbestemtId: UUID,
    error: Throwable,
) {
    "Klarte ikke hente inntektsmelding pga. feil i Redis.".also {
        logger.error(it)
        sikkerLogger.error(it, error)
    }
    when (error) {
        is RedisPollerTimeoutException -> {
            val response =
                ResultJson(
                    failure = RedisTimeoutResponse(inntektsmeldingTypeId = selvbestemtId).toJson(RedisTimeoutResponse.serializer()),
                )
            respondInternalServerError(response, ResultJson.serializer())
        }

        else -> {
            val response =
                ResultJson(
                    failure = RedisPermanentErrorResponse(selvbestemtId).toJson(RedisPermanentErrorResponse.serializer()),
                )
            respondInternalServerError(response, ResultJson.serializer())
        }
    }
}
