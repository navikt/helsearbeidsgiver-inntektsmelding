package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.serializer
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.Tekst
import no.nav.hag.simba.utils.felles.json.inntektMapSerializer
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.gjennomsnitt
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondForbidden
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

fun Route.inntektRoute(
    producer: Producer,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val redisPoller = RedisStore(redisConnection, RedisPrefix.Inntekt).let(::RedisPoller)

    post(Routes.INNTEKT) {
        val kontekstId = UUID.randomUUID()

        val request = call.receive<InntektRequest>()

        tilgangskontroll.validerTilgangTilForespoersel(call.request, request.forespoerselId)

        "Henter oppdatert inntekt for forespørselId: ${request.forespoerselId}".let {
            logger.info(it)
            sikkerLogger.info("$it og request:\n$request")
        }

        try {
            producer.sendRequestEvent(kontekstId, request)

            val resultatJson = redisPoller.hent(kontekstId)
            sikkerLogger.info("Fikk inntektresultat:\n$resultatJson")

            val resultat = resultatJson.success?.fromJson(inntektMapSerializer)
            if (resultat != null) {
                val response =
                    InntektResponse(
                        gjennomsnitt = resultat.gjennomsnitt(),
                        historikk = resultat,
                    ).toJson(InntektResponse.serializer())

                call.respond(HttpStatusCode.OK, response)
            } else {
                val feilmelding = resultatJson.failure?.fromJson(String.serializer()) ?: Tekst.TEKNISK_FEIL_FORBIGAAENDE
                respondInternalServerError(feilmelding, String.serializer())
            }
        } catch (_: ManglerAltinnRettigheterException) {
            respondForbidden("Du har ikke rettigheter for organisasjonen.", String.serializer())
        } catch (_: RedisPollerTimeoutException) {
            logger.info("Fikk timeout for forespørselId: ${request.forespoerselId}")
            respondInternalServerError(RedisTimeoutResponse(request.forespoerselId), RedisTimeoutResponse.serializer())
        }
    }
}

private fun Producer.sendRequestEvent(
    kontekstId: UUID,
    request: InntektRequest,
) {
    send(
        key = request.forespoerselId,
        message =
            mapOf(
                Key.EVENT_NAME to EventName.INNTEKT_REQUESTED.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to request.forespoerselId.toJson(),
                        Key.INNTEKTSDATO to request.inntektsdato.toJson(),
                    ).toJson(),
            ),
    )
}
