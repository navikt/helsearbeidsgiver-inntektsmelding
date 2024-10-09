package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.lesFnrFraAuthToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.JsonErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ValideringErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import java.util.UUID

fun Route.innsending(
    rapid: RapidsConnection,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val producer = InnsendingProducer(rapid)
    val redisPoller = RedisStore(redisConnection, RedisPrefix.Innsending).let(::RedisPoller)

    post(Routes.INNSENDING) {
        Metrics.innsendingEndpoint.recordTime(Route::innsending) {
            val transaksjonId = UUID.randomUUID()

            val skjema =
                call
                    .receiveText()
                    .runCatching {
                        parseJson()
                            .also { json ->
                                "Mottok inntektsmeldingsskjema.".let {
                                    logger.info(it)
                                    sikkerLogger.info("$it og request:\n$json")
                                }
                            }.fromJson(SkjemaInntektsmelding.serializer())
                    }.getOrElse { error ->
                        "Klarte ikke parse json for inntektsmeldingsskjema.".also {
                            logger.error(it)
                            sikkerLogger.error(it, error)
                        }
                        null
                    }

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
                    tilgangskontroll.validerTilgangTilForespoersel(call.request, skjema.forespoerselId)

                    val avsenderFnr = call.request.lesFnrFraAuthToken()

                    producer.publish(transaksjonId, skjema, avsenderFnr)

                    val resultat =
                        runCatching {
                            redisPoller.hent(transaksjonId)
                        }.map {
                            it.fromJson(ResultJson.serializer())
                        }

                    resultat
                        .onSuccess {
                            sikkerLogger.info("Fikk resultat for innsending:\n$it")

                            if (it.success != null) {
                                respond(HttpStatusCode.Created, InnsendingResponse(skjema.forespoerselId), InnsendingResponse.serializer())
                            } else {
                                val feilmelding = it.failure?.fromJson(String.serializer()) ?: Tekst.TEKNISK_FEIL_FORBIGAAENDE
                                respondInternalServerError(feilmelding, String.serializer())
                            }
                        }.onFailure {
                            sikkerLogger.info("Fikk timeout for forespørselId: ${skjema.forespoerselId}", it)
                            respondInternalServerError(RedisTimeoutResponse(skjema.forespoerselId), RedisTimeoutResponse.serializer())
                        }
                }
            }
        }
    }
}
