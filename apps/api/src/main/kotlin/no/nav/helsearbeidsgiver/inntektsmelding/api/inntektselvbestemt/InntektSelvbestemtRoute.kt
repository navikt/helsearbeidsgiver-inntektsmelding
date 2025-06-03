package no.nav.helsearbeidsgiver.inntektsmelding.api.inntektselvbestemt

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.domene.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.json.inntektMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.kafka.Producer
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.gjennomsnitt
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

fun Route.inntektSelvbestemtRoute(
    producer: Producer,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val redisPoller = RedisStore(redisConnection, RedisPrefix.InntektSelvbestemt).let(::RedisPoller)

    post(Routes.INNTEKT_SELVBESTEMT) {
        val kontekstId = UUID.randomUUID()

        val request = call.receive<InntektSelvbestemtRequest>()

        tilgangskontroll.validerTilgangTilOrg(call.request, request.orgnr)

        "Henter oppdatert inntekt for selvbestemt inntektsmelding".let {
            logger.info(it)
            sikkerLogger.info("$it og request:\n$request")
        }

        try {
            producer.sendRequestEvent(kontekstId, request)

            val resultatJson = redisPoller.hent(kontekstId)
            sikkerLogger.info("Fikk inntektsresultat for selvbestemt inntektsmelding:\n$resultatJson")

            val resultat = resultatJson.success?.fromJson(inntektMapSerializer)
            if (resultat != null) {
                val response =
                    InntektSelvbestemtResponse(
                        gjennomsnitt = resultat.gjennomsnitt(),
                        historikk = resultat,
                        bruttoinntekt = resultat.gjennomsnitt(),
                        tidligereInntekter = resultat.map { InntektPerMaaned(it.key, it.value) },
                    ).toJson(InntektSelvbestemtResponse.serializer())

                call.respond(HttpStatusCode.OK, response)
            } else {
                val feilmelding = resultatJson.failure?.fromJson(String.serializer()) ?: Tekst.TEKNISK_FEIL_FORBIGAAENDE
                respondInternalServerError(feilmelding, String.serializer())
            }
        } catch (e: ManglerAltinnRettigheterException) {
            respondForbidden("Du har ikke rettigheter for organisasjon.", String.serializer())
        } catch (_: RedisPollerTimeoutException) {
            logger.error("Fikk timeout for inntekt for selvbestemt inntektsmelding.")
            sikkerLogger.error("Fikk timeout for inntekt for selvbestemt inntektsmelding.")
            respondInternalServerError(RedisTimeoutResponse(), RedisTimeoutResponse.serializer())
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
