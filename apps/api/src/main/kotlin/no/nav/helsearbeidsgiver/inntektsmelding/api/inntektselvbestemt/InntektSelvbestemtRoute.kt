package no.nav.helsearbeidsgiver.inntektsmelding.api.inntektselvbestemt

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
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.validerTilgangOrgnrOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.hentResultatFraRedisOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.readRequestOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

fun Route.inntektSelvbestemtRoute(
    producer: Producer,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val redisPoller = RedisStore(redisConnection, RedisPrefix.InntektSelvbestemt).let(::RedisPoller)

    post(Routes.INNTEKT_SELVBESTEMT) {
        val kontekstId = UUID.randomUUID()

        readRequestOrError(
            kontekstId,
            InntektSelvbestemtRequest.serializer(),
        ) { request ->
            validerTilgangOrgnrOrError(tilgangskontroll, kontekstId, request.orgnr) {
                producer.sendRequestEvent(kontekstId, request)

                hentResultatFraRedisOrError(
                    redisPoller = redisPoller,
                    kontekstId = kontekstId,
                    logOnFailure = "Klarte ikke hente oppdatert inntekt for selvbestemt inntektsmelding pga. feil.",
                    successSerializer = inntektMapSerializer,
                ) {
                    sikkerLogger.info("Resultat for henting av inntekt for selvbestemt inntektsmelding:\n$it")

                    val response =
                        InntektSelvbestemtResponse(
                            gjennomsnitt = it.gjennomsnitt(),
                            historikk = it,
                        )

                    respondOk(response, InntektSelvbestemtResponse.serializer())
                }
            }
        }
    }
}

private fun Producer.sendRequestEvent(
    kontekstId: UUID,
    request: InntektSelvbestemtRequest,
) {
    send(
        key = request.sykmeldtFnr,
        message =
            mapOf(
                Key.EVENT_NAME to EventName.INNTEKT_SELVBESTEMT_REQUESTED.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FNR to request.sykmeldtFnr.toJson(),
                        Key.ORGNR_UNDERENHET to request.orgnr.toJson(),
                        Key.INNTEKTSDATO to request.inntektsdato.toJson(),
                    ).toJson(),
            ),
    )
}
