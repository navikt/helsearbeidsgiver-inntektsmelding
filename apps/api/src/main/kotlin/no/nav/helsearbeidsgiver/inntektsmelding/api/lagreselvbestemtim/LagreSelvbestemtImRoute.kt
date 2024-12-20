package no.nav.helsearbeidsgiver.inntektsmelding.api.lagreselvbestemtim

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
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
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.util.UUID

fun Route.lagreSelvbestemtImRoute(
    rapid: RapidsConnection,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val producer = LagreSelvbestemtImProducer(rapid)
    val redisPoller = RedisStore(redisConnection, RedisPrefix.LagreSelvbestemtIm).let(::RedisPoller)

    post(Routes.SELVBESTEMT_INNTEKTSMELDING) {
        val transaksjonId = UUID.randomUUID()

        MdcUtils.withLogFields(
            Log.apiRoute(Routes.SELVBESTEMT_INNTEKTSMELDING),
            Log.transaksjonId(transaksjonId),
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

                    producer.publish(transaksjonId, skjema, avsenderFnr)

                    val resultat =
                        runCatching {
                            redisPoller.hent(transaksjonId)
                        }

                    sendResponse(resultat)
                }
            }
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.lesRequestOrNull(): SkjemaInntektsmeldingSelvbestemt? =
    call
        .receiveText()
        .runCatching {
            parseJson()
                .also { json ->
                    "Mottok selvbestemt inntektsmelding.".let {
                        logger.info(it)
                        sikkerLogger.info("$it\n${json.toPretty()}")
                    }
                }.fromJson(SkjemaInntektsmeldingSelvbestemt.serializer())
        }.getOrElse { error ->
            "Kunne ikke parse json for selvbestemt inntektsmeldingsskjema.".let {
                logger.error(it)
                sikkerLogger.error(it, error)
            }
            null
        }

private suspend fun PipelineContext<Unit, ApplicationCall>.sendResponse(result: Result<ResultJson>) {
    result
        .onSuccess { resultJson ->
            val selvbestemtId = resultJson.success?.fromJson(UuidSerializer)
            if (selvbestemtId != null) {
                MdcUtils.withLogFields(
                    Log.selvbestemtId(selvbestemtId),
                ) {
                    "Selvbestemt inntektsmelding mottatt OK for ID '$selvbestemtId'.".also {
                        logger.info(it)
                        sikkerLogger.info(it)
                    }
                }
                respondOk(LagreSelvbestemtImResponse(selvbestemtId), LagreSelvbestemtImResponse.serializer())
            } else {
                val feilmelding = resultJson.failure?.fromJson(String.serializer()).orDefault("Tomt resultat i Redis.")

                "Klarte ikke motta selvbestemt inntektsmelding pga. feil.".also {
                    logger.error(it)
                    sikkerLogger.error("$it Feilmelding: '$feilmelding'")
                }

                if ("Mangler arbeidsforhold i perioden" == feilmelding) {
                    respondBadRequest(ArbeidsforholdErrorResponse(), ArbeidsforholdErrorResponse.serializer())
                } else {
                    respondInternalServerError(UkjentErrorResponse(), UkjentErrorResponse.serializer())
                }
            }
        }.onFailure { error ->
            "Klarte ikke hente resultat fra Redis.".also {
                logger.error(it)
                sikkerLogger.error(it, error)
            }
            when (error) {
                is RedisPollerTimeoutException ->
                    respondInternalServerError(RedisTimeoutResponse(), RedisTimeoutResponse.serializer())

                else ->
                    respondInternalServerError(RedisPermanentErrorResponse(), RedisPermanentErrorResponse.serializer())
            }
        }
}
