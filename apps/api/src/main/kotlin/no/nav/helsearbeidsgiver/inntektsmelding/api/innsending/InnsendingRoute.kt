package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.serializer
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.lesFnrFraAuthToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.time.LocalDateTime
import java.util.UUID
import no.nav.hag.simba.utils.felles.domene.SkjemaInntektsmeldingIntern as SkjemaInntektsmelding

fun Route.innsending(
    producer: Producer,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val redisPoller = RedisStore(redisConnection, RedisPrefix.Innsending).let(::RedisPoller)

    post(Routes.INNSENDING) {
        val kontekstId = UUID.randomUUID()
        val mottatt = LocalDateTime.now()

        val skjema = lesRequestOrNull()
        when {
            skjema == null -> {
                respondBadRequest(ErrorResponse.JsonSerialization(kontekstId))
            }

            skjema.inntekt?.naturalytelser.orEmpty() != skjema.naturalytelser -> {
                "Naturalytelser på rotnivå og under 'inntekt' stemmer ikke overens. Inntektsmelding avvises.".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }
                respondInternalServerError(ErrorResponse.Unknown(kontekstId))
            }

            skjema.valider().isNotEmpty() -> {
                val valideringsfeil = skjema.valider()

                "Fikk valideringsfeil: $valideringsfeil".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }

                val response = ErrorResponse.Validering(kontekstId, valideringsfeil)

                respondBadRequest(response)
            }

            else -> {
                tilgangskontroll.validerTilgangTilForespoersel(call.request, skjema.forespoerselId)

                val avsenderFnr = call.request.lesFnrFraAuthToken()

                producer.sendRequestEvent(kontekstId, avsenderFnr, skjema, mottatt)

                val resultatJson = redisPoller.hent(kontekstId)
                if (resultatJson != null) {
                    sikkerLogger.info("Fikk resultat for innsending:\n$resultatJson")

                    if (resultatJson.success != null) {
                        respond(HttpStatusCode.Created, InnsendingResponse(skjema.forespoerselId), InnsendingResponse.serializer())
                    } else {
                        val feilmelding = resultatJson.failure?.fromJson(String.serializer())
                        respondInternalServerError(feilmelding)
                    }
                } else {
                    respondInternalServerError(ErrorResponse.RedisTimeout(skjema.forespoerselId))
                }
            }
        }
    }
}

private suspend fun RoutingContext.lesRequestOrNull(): SkjemaInntektsmelding? =
    call
        .receiveText()
        .runCatching {
            parseJson()
                .also { json ->
                    "Mottok inntektsmeldingsskjema.".let {
                        logger.info(it)
                        sikkerLogger.info("$it\n${json.toPretty()}")
                    }
                }.fromJson(SkjemaInntektsmelding.serializer())
        }.getOrElse { error ->
            "Klarte ikke parse json for inntektsmeldingsskjema.".also {
                logger.error(it)
                sikkerLogger.error(it, error)
            }
            null
        }

private fun Producer.sendRequestEvent(
    kontekstId: UUID,
    arbeidsgiverFnr: Fnr,
    skjema: SkjemaInntektsmelding,
    mottatt: LocalDateTime,
) {
    send(
        key = skjema.forespoerselId,
        message =
            mapOf(
                Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.ARBEIDSGIVER_FNR to arbeidsgiverFnr.toJson(),
                        Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmelding.serializer()),
                        Key.MOTTATT to mottatt.toJson(),
                    ).toJson(),
            ),
    )
}
