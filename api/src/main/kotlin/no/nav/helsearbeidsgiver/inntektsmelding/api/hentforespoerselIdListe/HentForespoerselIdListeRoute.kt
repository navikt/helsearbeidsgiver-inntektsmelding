package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoerselIdListe

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.prometheus.client.Summary
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.domene.HentForespoerslerForVedtaksperiodeIdListeResultat
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.domene.VedtaksperiodeIdForespoerselIdPar
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
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.receive
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondForbidden
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import java.util.UUID

fun Route.hentForespoerselIdListeRoute(
    rapid: RapidsConnection,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val hentForespoerslerProducer = HentForespoerslerProducer(rapid)
    val redisPoller = RedisStore(redisConnection, RedisPrefix.HentForespoerslerForVedtaksperiodeIdListe).let(::RedisPoller)

    val requestLatency =
        Summary
            .build()
            .name("simba_hent_forespoersel_id_liste_latency_seconds")
            .help("hent forespoersel id liste endpoint latency in seconds")
            .register()

    post(Routes.HENT_FORESPOERSEL_ID_LISTE) {
        val transaksjonId = UUID.randomUUID()

        val requestTimer = requestLatency.startTimer()

        runCatching {
            receive(HentForespoerslerRequest.serializer())
        }.onSuccess { request ->
            logger.info("Henter forespørsler for liste med vedtaksperiode-IDer: ${request.vedtaksperiodeIdListe}")
            try {
                hentForespoerslerProducer.publish(transaksjonId, request)

                val resultatJson = redisPoller.hent(transaksjonId).fromJson(ResultJson.serializer())

                sikkerLogger.info("Hentet forespørslene: $resultatJson")

                val resultat = resultatJson.success?.fromJson(HentForespoerslerForVedtaksperiodeIdListeResultat.serializer())

                if (resultat != null) {
                    val orgnrSet = resultat.forespoersler.map { it.value.orgnr }.toSet()

                    when {
                        orgnrSet.size > 1 ->
                            respondBadRequest("Ikke tillat å hente forespoersler som tilhører ulike arbeidsgivere.", String.serializer())

                        else -> {
                            orgnrSet.firstOrNull()?.also { orgnr -> tilgangskontroll.validerTilgangTilOrg(call.request, orgnr) }

                            val respons =
                                resultat.forespoersler.map {
                                    VedtaksperiodeIdForespoerselIdPar(
                                        forespoerselId = it.key,
                                        vedtaksperiodeId = it.value.vedtaksperiodeId,
                                    )
                                }

                            respond(
                                HttpStatusCode.OK,
                                respons,
                                VedtaksperiodeIdForespoerselIdPar.serializer().list(),
                            )
                        }
                    }
                } else {
                    val feilmelding = resultatJson.failure?.fromJson(String.serializer()) ?: "Teknisk feil, prøv igjen senere."
                    respondInternalServerError(feilmelding, String.serializer())
                }
            } catch (e: ManglerAltinnRettigheterException) {
                respondForbidden("Mangler rettigheter for organisasjon.", String.serializer())
            } catch (_: RedisPollerTimeoutException) {
                logger.info("Fikk timeout ved henting av forespørselIDer for vedtaksperiodeIDene: ${request.vedtaksperiodeIdListe}")
                respondInternalServerError(RedisTimeoutResponse(), RedisTimeoutResponse.serializer())
            } catch (e: Exception) {
                logger.error("Ukjent feil ved henting av forespørselIDer for vedtaksperiodeIDene: ${request.vedtaksperiodeIdListe}", e)
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
