package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.InntektData
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.authorize
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondForbidden
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty

// TODO Mangler tester
fun RouteExtra.inntektRoute() {
    val inntektProducer = InntektProducer(connection)
    val tilgangProducer = TilgangProducer(connection)

    route.route(Routes.INNTEKT) {
        post {
            val request = call.receive<InntektRequest>()

            authorize(
                forespoerselId = request.forespoerselId,
                tilgangProducer = tilgangProducer,
                redisPoller = redis,
                cache = tilgangCache
            )

            "Henter oppdatert inntekt for forespørselId: ${request.forespoerselId}".let {
                logger.info(it)
                sikkerLogger.info("$it og request:\n$request")
            }

            try {
                val clientId = inntektProducer.publish(request)

                val resultat = redis.hent(clientId)
                sikkerLogger.info("Fikk resultat:\n${resultat.toPretty()}")

                val inntektResponse = resultat.fromJson(InntektData.serializer()).toResponse()

                val inntektResultatJson = inntektResponse.resultat.toJson(InntektResultat.serializer().nullable)

                // TODO Må fikse på frontendkoden før vi kan svare med InntektResponse, og ikke bare InntektResultat som nå
                call.respond(inntektResponse.status(), inntektResultatJson)
            } catch (e: ManglerAltinnRettigheterException) {
                respondForbidden("Du har ikke rettigheter for organisasjon.", String.serializer())
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout for forespørselId: ${request.forespoerselId}")
                respondInternalServerError(RedisTimeoutResponse(request.forespoerselId), RedisTimeoutResponse.serializer())
            }
        }
    }
}

private fun InntektData.toResponse(): InntektResponse =
    InntektResponse(
        resultat = inntekt?.let {
            InntektResultat(
                bruttoinntekt = it.gjennomsnitt(),
                tidligereInntekter = it.maanedOversikt
            )
        },
        feilReport = feil
    )

private fun InntektResponse.status() =
    if (feilReport != null) {
        HttpStatusCode.InternalServerError
    } else {
        HttpStatusCode.OK
    }
