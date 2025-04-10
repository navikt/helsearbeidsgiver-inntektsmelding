package no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.domene.AktiveArbeidsgivere
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.lesFnrFraAuthToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondNotFound
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

fun Route.aktiveOrgnrRoute(
    connection: RapidsConnection,
    redisConnection: RedisConnection,
) {
    val aktiveOrgnrProducer = AktiveOrgnrProducer(connection)
    val redisPoller = RedisStore(redisConnection, RedisPrefix.AktiveOrgnr).let(::RedisPoller)

    post(Routes.AKTIVEORGNR) {
        val kontekstId = UUID.randomUUID()

        try {
            val request = call.receive<AktiveOrgnrRequest>()
            val arbeidsgiverFnr = call.request.lesFnrFraAuthToken()

            aktiveOrgnrProducer.publish(kontekstId, arbeidsgiverFnr = arbeidsgiverFnr, arbeidstagerFnr = request.identitetsnummer)

            val resultatJson = redisPoller.hent(kontekstId)

            val resultat = resultatJson.success?.fromJson(AktiveArbeidsgivere.serializer())
            if (resultat != null) {
                if (resultat.underenheter.isEmpty()) {
                    respondNotFound("Fant ingen arbeidsforhold.", String.serializer())
                } else {
                    val response = resultat.toResponse()
                    call.respond(HttpStatusCode.Created, response.toJson(AktiveOrgnrResponse.serializer()))
                }
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

private fun AktiveArbeidsgivere.toResponse(): AktiveOrgnrResponse =
    AktiveOrgnrResponse(
        fulltNavn = fulltNavn,
        avsenderNavn = avsenderNavn,
        underenheter =
            underenheter.map {
                GyldigUnderenhet(
                    orgnrUnderenhet = it.orgnrUnderenhet,
                    virksomhetsnavn = it.virksomhetsnavn,
                )
            },
    )
