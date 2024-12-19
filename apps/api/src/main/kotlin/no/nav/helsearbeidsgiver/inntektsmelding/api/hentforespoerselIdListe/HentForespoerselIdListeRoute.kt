package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoerselIdListe

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.Tekst.TEKNISK_FEIL_FORBIGAAENDE
import no.nav.helsearbeidsgiver.felles.Tekst.UGYLDIG_REQUEST
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.VedtaksperiodeIdForespoerselIdPar
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
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
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import java.util.UUID

const val MAKS_ANTALL_VEDTAKSPERIODE_IDER = 100

fun Route.hentForespoerselIdListe(
    rapid: RapidsConnection,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val hentForespoerslerProducer = HentForespoerslerProducer(rapid)
    val redisPoller = RedisStore(redisConnection, RedisPrefix.HentForespoerslerForVedtaksperiodeIdListe).let(::RedisPoller)

    post(Routes.HENT_FORESPOERSEL_ID_LISTE) {
        Metrics.hentForespoerselIdListeEndpoint.recordTime(Route::hentForespoerselIdListe) {
            runCatching {
                receive(HentForespoerslerRequest.serializer())
            }.onSuccess { request ->
                if (request.vedtaksperiodeIdListe.size > MAKS_ANTALL_VEDTAKSPERIODE_IDER) {
                    loggErrorSikkerOgUsikker(
                        "Stopper forsøk på å hente forespørsler for mer enn $MAKS_ANTALL_VEDTAKSPERIODE_IDER vedtaksperiode-IDer på en gang.",
                    )
                    respondBadRequest(UGYLDIG_REQUEST, String.serializer())
                } else {
                    try {
                        hentForespoersler(request, hentForespoerslerProducer, redisPoller, tilgangskontroll)
                    } catch (_: ManglerAltinnRettigheterException) {
                        respondForbidden("Mangler rettigheter for organisasjon.", String.serializer())
                    } catch (e: RedisPollerTimeoutException) {
                        loggErrorSikkerOgUsikker("Fikk timeout ved henting av forespørselIDer for vedtaksperiodeIDene: ${request.vedtaksperiodeIdListe}", e)
                        respondInternalServerError(RedisTimeoutResponse(), RedisTimeoutResponse.serializer())
                    } catch (e: Exception) {
                        loggErrorSikkerOgUsikker("Ukjent feil ved henting av forespørselIDer for vedtaksperiodeIDene: ${request.vedtaksperiodeIdListe}", e)
                        respondInternalServerError(TEKNISK_FEIL_FORBIGAAENDE, String.serializer())
                    }
                }
            }.onFailure {
                "Klarte ikke lese request.".let { feilMelding ->
                    loggErrorSikkerOgUsikker(feilMelding, it)
                    respondBadRequest(feilMelding, String.serializer())
                }
            }
        }
    }
}

suspend fun PipelineContext<Unit, ApplicationCall>.hentForespoersler(
    request: HentForespoerslerRequest,
    hentForespoerslerProducer: HentForespoerslerProducer,
    redisPoller: RedisPoller,
    tilgangskontroll: Tilgangskontroll,
) {
    loggInfoSikkerOgUsikker("Henter forespørsler for liste med vedtaksperiode-IDer: ${request.vedtaksperiodeIdListe}")

    val transaksjonId = UUID.randomUUID()

    hentForespoerslerProducer.publish(transaksjonId, request)

    val resultatJson = redisPoller.hent(transaksjonId)

    sikkerLogger.info("Hentet forespørslene: $resultatJson")

    when (val resultat = resultatJson.success?.fromJson(MapSerializer(UuidSerializer, Forespoersel.serializer()))) {
        null -> {
            val feilmelding = resultatJson.failure?.fromJson(String.serializer()) ?: TEKNISK_FEIL_FORBIGAAENDE
            respondInternalServerError(feilmelding, String.serializer())
        }

        else -> {
            val orgnrSet = resultat.map { (_, forespoersel) -> forespoersel.orgnr }.toSet()

            when {
                orgnrSet.size > 1 -> {
                    "Stopper forsøk på å hente forespørsler for vedtaksperioder, fordi de tilhører ulike arbeidsgivere.".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }
                    respondBadRequest(UGYLDIG_REQUEST, String.serializer())
                }

                else -> {
                    orgnrSet.firstOrNull()?.also { orgnr -> tilgangskontroll.validerTilgangTilOrg(call.request, orgnr.verdi) }

                    val respons =
                        resultat.map { (id, forespoersel) ->
                            VedtaksperiodeIdForespoerselIdPar(
                                forespoerselId = id,
                                vedtaksperiodeId = forespoersel.vedtaksperiodeId,
                            )
                        }

                    respond(
                        HttpStatusCode.OK,
                        respons,
                        VedtaksperiodeIdForespoerselIdPar.serializer().list(),
                    )
                }
            }
        }
    }
}

private fun loggInfoSikkerOgUsikker(loggMelding: String) {
    loggMelding.also {
        logger.info(it)
        sikkerLogger.info(it)
    }
}

private fun loggErrorSikkerOgUsikker(
    loggMelding: String,
    throwable: Throwable? = null,
) {
    if (throwable != null) {
        loggMelding.also {
            logger.error(it)
            sikkerLogger.error(it, throwable)
        }
    } else {
        loggMelding.also {
            logger.error(it)
            sikkerLogger.error(it)
        }
    }
}
