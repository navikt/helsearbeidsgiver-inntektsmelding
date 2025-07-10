package no.nav.helsearbeidsgiver.inntektsmelding.api.lagreselvbestemtim

import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.jsonObject
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.ArbeidsforholdType
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
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
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.time.LocalDateTime
import java.util.UUID

fun Route.lagreSelvbestemtImRoute(
    producer: Producer,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val redisPoller = RedisStore(redisConnection, RedisPrefix.LagreSelvbestemtIm).let(::RedisPoller)

    post(Routes.SELVBESTEMT_INNTEKTSMELDING) {
        val kontekstId = UUID.randomUUID()
        val mottatt = LocalDateTime.now()

        MdcUtils.withLogFields(
            Log.apiRoute(Routes.SELVBESTEMT_INNTEKTSMELDING),
            Log.kontekstId(kontekstId),
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
                    tilgangskontroll.validerTilgangTilOrg(call.request, skjema.avsender.orgnr)

                    val avsenderFnr = call.request.lesFnrFraAuthToken()

                    producer.sendRequestEvent(kontekstId, avsenderFnr, skjema, mottatt)

                    val resultat =
                        runCatching {
                            redisPoller.hent(kontekstId)
                        }

                    sendResponse(resultat)
                }
            }
        }
    }
}

private suspend fun RoutingContext.lesRequestOrNull(): SkjemaInntektsmeldingSelvbestemt? =
    call
        .receiveText()
        .runCatching {
            parseJson()
                .also { json ->
                    "Mottok selvbestemt inntektsmelding.".let {
                        logger.info(it)
                        sikkerLogger.info("$it\n${json.toPretty()}")
                    }
                }.let {
                    // TODO: Midlertidig håndtering av manglende arbeidsforholdType kan fjernes når frontend er oppdatert
                    val arbeidforholdTypeJson = it.jsonObject[SkjemaInntektsmeldingSelvbestemt::arbeidsforholdType.name]
                    if (arbeidforholdTypeJson != null) {
                        it
                    } else {
                        val vedtaksperiodeId = it.jsonObject[SkjemaInntektsmeldingSelvbestemt::vedtaksperiodeId.name]?.fromJson(UuidSerializer)
                        val arbeidsforholdType = ArbeidsforholdType.MedArbeidsforhold(vedtaksperiodeId = requireNotNull(vedtaksperiodeId))
                        it.jsonObject
                            .plus(
                                SkjemaInntektsmeldingSelvbestemt::arbeidsforholdType.name to arbeidsforholdType.toJson(ArbeidsforholdType.serializer()),
                            ).toJson()
                    }
                }.fromJson(SkjemaInntektsmeldingSelvbestemt.serializer())
        }.getOrElse { error ->
            "Kunne ikke parse json for selvbestemt inntektsmeldingsskjema.".let {
                logger.error(it)
                sikkerLogger.error(it, error)
            }
            null
        }

private fun Producer.sendRequestEvent(
    kontekstId: UUID,
    avsenderFnr: Fnr,
    skjema: SkjemaInntektsmeldingSelvbestemt,
    mottatt: LocalDateTime,
) {
    send(
        key = skjema.sykmeldtFnr,
        message =
            mapOf(
                Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.ARBEIDSGIVER_FNR to avsenderFnr.toJson(),
                        Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmeldingSelvbestemt.serializer()),
                        Key.MOTTATT to mottatt.toJson(),
                    ).toJson(),
            ),
    )
}

private suspend fun RoutingContext.sendResponse(result: Result<ResultJson>) {
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
