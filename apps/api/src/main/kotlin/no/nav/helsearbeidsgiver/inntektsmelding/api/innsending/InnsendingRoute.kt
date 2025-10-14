package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.serializer
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.Tekst
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
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
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.time.LocalDateTime
import java.util.UUID

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

                producer.sendRequestEvent(kontekstId, avsenderFnr, skjema, mottatt)

                val resultat = runCatching { redisPoller.hent(kontekstId) }

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
    avsenderFnr: Fnr,
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
                        Key.ARBEIDSGIVER_FNR to avsenderFnr.toJson(),
                        Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmelding.serializer()),
                        Key.MOTTATT to mottatt.toJson(),
                    ).toJson(),
            ),
    )
}
