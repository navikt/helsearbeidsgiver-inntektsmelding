package no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.AktiveArbeidsgivere
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.lesFnrFraAuthToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
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

                val resultatJson = redis.hent(clientId).fromJson(ResultJson.serializer())

                val resultat = resultatJson.success?.fromJson(AktiveArbeidsgivere.serializer())
                if (resultat != null) {
                    val response = resultat.toResponse()
                    call.respond(HttpStatusCode.Created, response.toJson(AktiveOrgnrResponse.serializer()))
                } else {
                    val feilmelding = resultatJson.failure?.fromJson(String.serializer()) ?: Tekst.TEKNISK_FEIL_FORBIGAAENDE
                    respondInternalServerError(feilmelding, String.serializer())
                }
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout mot redis ved henting av aktive orgnr")
                respondInternalServerError(Tekst.TEKNISK_FEIL_FORBIGAAENDE, String.serializer())
            } catch (e: Exception) {
                sikkerLogger.error("Feil ved henting av aktive orgnr", e)
                respondInternalServerError(Tekst.TEKNISK_FEIL_FORBIGAAENDE, String.serializer())
            }
        }
    }
}

private fun AktiveArbeidsgivere.toResponse(): AktiveOrgnrResponse =
    AktiveOrgnrResponse(
        fulltNavn = fulltNavn,
        underenheter = underenheter.map {
            GyldigUnderenhet(
                orgnrUnderenhet = it.orgnrUnderenhet,
                virksomhetsnavn = it.virksomhetsnavn
            )
        }
    )
