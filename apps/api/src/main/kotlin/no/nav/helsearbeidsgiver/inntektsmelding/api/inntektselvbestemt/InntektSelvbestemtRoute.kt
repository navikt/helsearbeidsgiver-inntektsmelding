package no.nav.helsearbeidsgiver.inntektsmelding.api.inntektselvbestemt

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.domene.Inntekt
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
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
    rapid: RapidsConnection,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val inntektSelvbestemtProducer = InntektSelvbestemtProducer(rapid)
    val redisPoller = RedisStore(redisConnection, RedisPrefix.InntektSelvbestemt).let(::RedisPoller)

    post(Routes.INNTEKT_SELVBESTEMT) {
        val kontekstId = UUID.randomUUID()

        val request = call.receive<InntektSelvbestemtRequest>()

        tilgangskontroll.validerTilgangTilOrg(call.request, request.orgnr.verdi)

        "Henter oppdatert inntekt for selvbestemt inntektsmelding".let {
            logger.info(it)
            sikkerLogger.info("$it og request:\n$request")
        }

        try {
            inntektSelvbestemtProducer.publish(kontekstId, request)

            val resultatJson = redisPoller.hent(kontekstId)
            sikkerLogger.info("Fikk inntektsresultat for selvbestemt inntektsmelding:\n$resultatJson")

            val resultat = resultatJson.success?.fromJson(Inntekt.serializer())
            if (resultat != null) {
                val response =
                    InntektSelvbestemtResponse(
                        bruttoinntekt = resultat.gjennomsnitt(),
                        tidligereInntekter = resultat.maanedOversikt,
                    ).toJson(InntektSelvbestemtResponse.serializer().nullable)

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
