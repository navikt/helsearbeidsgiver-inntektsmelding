package no.nav.helsearbeidsgiver.inntektsmelding.api.lagreselvbestemtim

import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.serializer
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.lesFnrFraAuthToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.validerTilgangOrgnrOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.hentResultatFraRedisOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.readRequestOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
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
            readRequestOrError(
                kontekstId,
                SkjemaInntektsmeldingSelvbestemt.serializer(),
            ) { skjema ->
                if (skjema.valider().isNotEmpty()) {
                    val valideringsfeil = skjema.valider()

                    "Fikk valideringsfeil: $valideringsfeil".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }

                    respondError(ErrorResponse.Validering(kontekstId, valideringsfeil))
                } else {
                    validerTilgangOrgnrOrError(tilgangskontroll, kontekstId, skjema.avsender.orgnr) {
                        val avsenderFnr = call.request.lesFnrFraAuthToken()

                        producer.sendRequestEvent(kontekstId, avsenderFnr, skjema, mottatt)

                        hentResultatFraRedisOrError(
                            redisPoller = redisPoller,
                            kontekstId = kontekstId,
                            logOnFailure = "Klarte ikke motta selvbestemt inntektsmelding pga. feil.",
                            onFailureCustomError = {
                                val feilmelding = it?.fromJson(String.serializer())
                                if (feilmelding == "Mangler arbeidsforhold i perioden") {
                                    ErrorResponse.Arbeidsforhold(kontekstId)
                                } else {
                                    null
                                }
                            },
                            successSerializer = UuidSerializer,
                        ) { selvbestemtId ->
                            MdcUtils.withLogFields(
                                Log.selvbestemtId(selvbestemtId),
                            ) {
                                "Selvbestemt inntektsmelding mottatt OK for ID '$selvbestemtId'.".also {
                                    logger.info(it)
                                    sikkerLogger.info(it)
                                }
                                respondOk(LagreSelvbestemtImResponse(selvbestemtId), LagreSelvbestemtImResponse.serializer())
                            }
                        }
                    }
                }
            }
        }
    }
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
