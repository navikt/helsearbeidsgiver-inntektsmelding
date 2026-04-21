package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.inntektMapSerializer
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.gjennomsnitt
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.validerTilgangForespoerselOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.hentResultatFraRedisOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.readRequestOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
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

        readRequestOrError(
            kontekstId,
            InntektRequest.serializer(),
        ) { request ->
            validerTilgangForespoerselOrError(tilgangskontroll, kontekstId, request.forespoerselId) {
                "Henter oppdatert inntekt for forespoerselId: ${request.forespoerselId}".let {
                    logger.info(it)
                    sikkerLogger.info("$it og request:\n$request")
                }

                producer.sendRequestEvent(kontekstId, request)

                hentResultatFraRedisOrError(
                    redisPoller = redisPoller,
                    kontekstId = kontekstId,
                    inntektsmeldingTypeId = request.forespoerselId,
                    logOnFailure = "Klarte ikke hente oppdatert inntekt pga. feil.",
                    successSerializer = inntektMapSerializer,
                ) {
                    sikkerLogger.info("Resultat for henting av inntekt:\n$it")

                    val response =
                        InntektResponse(
                            gjennomsnitt = it.gjennomsnitt(),
                            historikk = it,
                        )

                    respondOk(response, InntektResponse.serializer())
                }
            }
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
