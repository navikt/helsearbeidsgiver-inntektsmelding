package no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.lesFnrFraAuthToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson

fun Route.aktiveOrgnrRoute(
    connection: RapidsConnection,
    redis: RedisPoller
) {
    val aktiveOrgnrProducer = AktiveOrgnrProducer(connection)
    route(Routes.AKTIVEORGNR) {
        post {
            try {
                val request = call.receive<AktiveOrgnrRequest>()
                val arbeidsgiverFnr = call.request.lesFnrFraAuthToken()
                val clientId = aktiveOrgnrProducer.publish(arbeidsgiverFnr = arbeidsgiverFnr, arbeidstagerFnr = request.identitetsnummer)
                val resultat = redis.hent(clientId).fromJson(AktiveOrgnrResponse.serializer())
                call.respond(HttpStatusCode.Created, resultat.toJson(AktiveOrgnrResponse.serializer()))
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout mot redis ved henting av aktive orgnr")
                call.respond(HttpStatusCode.InternalServerError, "{}")
            } catch (e: Exception) {
                sikkerLogger.error("Feil ved henting av aktive orgnr", e)
                call.respond(HttpStatusCode.InternalServerError, "{}")
            }
        }
    }
}
