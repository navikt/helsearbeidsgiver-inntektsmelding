package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.resultat.lagreim.LagreImError
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
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
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.validerTilgangForespoersel
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.hentResultatFraRedis
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.readRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondCreated
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondError
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
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

        readRequest(
            kontekstId,
            SkjemaInntektsmelding.serializer(),
        ) { skjema ->
            if (skjema.valider().isNotEmpty()) {
                val valideringsfeil = skjema.valider()

                "Fikk valideringsfeil: $valideringsfeil".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }

                respondError(ErrorResponse.Validering(kontekstId, valideringsfeil))
            } else {
                validerTilgangForespoersel(tilgangskontroll, kontekstId, skjema.forespoerselId) {
                    val avsenderFnr = call.request.lesFnrFraAuthToken()

                    producer.sendRequestEvent(kontekstId, skjema, avsenderFnr, mottatt)

                    hentResultatFraRedis(
                        redisPoller = redisPoller,
                        kontekstId = kontekstId,
                        inntektsmeldingTypeId = skjema.forespoerselId,
                        logOnFailure = "Klarte ikke motta forespurt inntektsmelding pga. feil.",
                        onFailureCustomError = { failure ->
                            failure
                                ?.fromJson(LagreImError.serializer())
                                ?.feiletValidering
                                ?.let { ErrorResponse.Validering(kontekstId, setOf(it)) }
                        },
                        successSerializer = JsonElement.serializer(),
                    ) {
                        sikkerLogger.info("Fikk resultat for innsending:\n$it")
                        respondCreated(InnsendingResponse(skjema.forespoerselId), InnsendingResponse.serializer())
                    }
                }
            }
        }
    }
}

private fun Producer.sendRequestEvent(
    kontekstId: UUID,
    skjema: SkjemaInntektsmelding,
    arbeidsgiverFnr: Fnr,
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
                        Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmelding.serializer()),
                        Key.ARBEIDSGIVER_FNR to arbeidsgiverFnr.toJson(),
                        Key.MOTTATT to mottatt.toJson(),
                    ).toJson(),
            ),
    )
}
