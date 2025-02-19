package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.fyllUtMangledeEndringsAarsaker
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
import no.nav.helsearbeidsgiver.utils.json.toPretty
import java.time.LocalDateTime
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

                    producer.publish(kontekstId, avsenderFnr, skjema, mottatt)

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
}

// // midlertidlig duplisering for å støtte flere endringsÅrsaker
// fun SkjemaInntektsmelding.fyllUtMangledeEndringsAarsaker(): SkjemaInntektsmelding {
//    val medEndringsAarsakerfyllt = this.copy(inntekt = this.inntekt?.copy(endringAarsaker = listOfNotNull(this.inntekt?.endringAarsak)))
//    val verdiMangler = this.inntekt?.endringAarsaker.isNullOrEmpty()
//    return if (verdiMangler) medEndringsAarsakerfyllt else this
// }
//
// fun Inntektsmelding.fyllUtMangledeEndringsAarsaker(): Inntektsmelding {
//    val medEndringsAarsakerfyllt = this.copy(inntekt = this.inntekt?.copy(endringAarsaker = listOfNotNull(this.inntekt?.endringAarsak)))
//    val verdiMangler = this.inntekt?.endringAarsaker.isNullOrEmpty()
//    return if (verdiMangler) medEndringsAarsakerfyllt else this
// }

private suspend fun PipelineContext<Unit, ApplicationCall>.lesRequestOrNull(): SkjemaInntektsmelding? =
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
                .fyllUtMangledeEndringsAarsaker()
        }.getOrElse { error ->
            "Klarte ikke parse json for inntektsmeldingsskjema.".also {
                logger.error(it)
                sikkerLogger.error(it, error)
            }
            null
        }
