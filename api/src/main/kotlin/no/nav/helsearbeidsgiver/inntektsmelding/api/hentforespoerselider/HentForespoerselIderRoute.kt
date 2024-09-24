package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoerselider

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.prometheus.client.Summary
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.domene.HentForespoerselIderResultat
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
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
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.receive
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondForbidden
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.utils.json.fromJson
import java.util.UUID

fun Route.hentForespoerselIderRoute(
    rapid: RapidsConnection,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val hentForespoerselProducer = HentForespoerselIderProducer(rapid)
    val redisPoller = RedisStore(redisConnection, RedisPrefix.HentForespoerselIder).let(::RedisPoller)

    val requestLatency =
        Summary
            .build()
            .name("simba_hent_forespoersel_ider_latency_seconds")
            .help("hent forespoersel ider endpoint latency in seconds")
            .register()

    post(Routes.HENT_FORESPOERSEL_IDER) {
        val transaksjonId = UUID.randomUUID()

        val requestTimer = requestLatency.startTimer()

        runCatching {
            receive(HentForespoerselIderRequest.serializer())
        }.onSuccess { request ->
            logger.info("Henter forespørselIDer for vedtaksperiodeIDene: ${request.vedtaksperiodeIder}")
            try {
                tilgangskontroll.validerTilgangTilOrg(call.request, request.orgnr.verdi)

                hentForespoerselProducer.publish(transaksjonId, request)

                val resultatJson = redisPoller.hent(transaksjonId).fromJson(ResultJson.serializer())

                logger.info("Hentet forespørselIDene: $resultatJson")

                val resultat = resultatJson.success?.fromJson(HentForespoerselIderResultat.serializer())

                if (resultat != null) {
                    respond(HttpStatusCode.OK, HentForespoerselIderResponse(resultat.ider), HentForespoerselIderResponse.serializer())
                } else {
                    val feilmelding = resultatJson.failure?.fromJson(String.serializer()) ?: "Teknisk feil, prøv igjen senere."
                    respondInternalServerError(feilmelding, String.serializer())
                }
            } catch (e: ManglerAltinnRettigheterException) {
                respondForbidden("Mangler rettigheter for organisasjon.", String.serializer())
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout ved henting av forespørselIDer for vedtaksperiodeIDene: ${request.vedtaksperiodeIder}")
                respondInternalServerError(RedisTimeoutResponse(), RedisTimeoutResponse.serializer())
            } catch (e: Exception) {
                logger.error("Ukjent feil ved henting av forespørselIDer for vedtaksperiodeIDene: ${request.vedtaksperiodeIder}", e)
                respondInternalServerError("Teknisk feil, prøv igjen senere.", String.serializer())
            }
        }.also {
            requestTimer.observeDuration()
        }.onFailure {
            "Klarte ikke lese request.".let { feilMelding ->
                logger.error(feilMelding, it)
                respondBadRequest(feilMelding, String.serializer())
            }
        }
    }
}
