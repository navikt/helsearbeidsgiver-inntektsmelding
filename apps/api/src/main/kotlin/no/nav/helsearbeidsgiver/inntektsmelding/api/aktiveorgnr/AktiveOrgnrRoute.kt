package no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.domene.AktiveArbeidsgivere
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.kafka.Producer
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
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

fun Route.aktiveOrgnrRoute(
    producer: Producer,
    redisConnection: RedisConnection,
) {
    val redisPoller = RedisStore(redisConnection, RedisPrefix.AktiveOrgnr).let(::RedisPoller)

    post(Routes.AKTIVEORGNR) {
        val kontekstId = UUID.randomUUID()

        try {
            val request = call.receive<AktiveOrgnrRequest>()
            val arbeidsgiverFnr = call.request.lesFnrFraAuthToken()

            producer.sendRequestEvent(kontekstId, arbeidsgiverFnr = arbeidsgiverFnr, arbeidstakerFnr = request.identitetsnummer)

            val resultatJson = redisPoller.hent(kontekstId)

            val resultat = resultatJson.success?.fromJson(AktiveArbeidsgivere.serializer())
            if (resultat != null) {
                if (resultat.underenheter.isEmpty()) {
                    respondNotFound("Fant ingen arbeidsforhold.", String.serializer())
                } else {
                    val response = resultat.toResponse()
                    call.respond(HttpStatusCode.OK, response.toJson(AktiveOrgnrResponse.serializer()))
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

private fun Producer.sendRequestEvent(
    kontekstId: UUID,
    arbeidsgiverFnr: Fnr,
    arbeidstakerFnr: Fnr,
) {
    send(
        key = arbeidstakerFnr,
        message =
            mapOf(
                Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FNR to arbeidstakerFnr.toJson(),
                        Key.ARBEIDSGIVER_FNR to arbeidsgiverFnr.toJson(),
                    ).toJson(),
            ),
    )
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
