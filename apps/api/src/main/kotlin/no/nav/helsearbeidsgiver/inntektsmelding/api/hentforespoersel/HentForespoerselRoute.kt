package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoersel

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.domene.HentForespoerselResultat
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.lesFnrFraAuthToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.receive
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondForbidden
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

fun Route.hentForespoersel(
    rapid: RapidsConnection,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val hentForespoerselProducer = HentForespoerselProducer(rapid)
    val redisPoller = RedisStore(redisConnection, RedisPrefix.HentForespoersel).let(::RedisPoller)

    post(Routes.HENT_FORESPOERSEL) {
        val kontekstId = UUID.randomUUID()

        Metrics.hentForespoerselEndpoint.recordTime(Route::hentForespoersel) {
            runCatching {
                receive(HentForespoerselRequest.serializer())
            }.onSuccess { request ->
                logger.info("Henter data for uuid: ${request.uuid}")
                try {
                    tilgangskontroll.validerTilgangTilForespoersel(call.request, request.uuid)

                    val arbeidsgiverFnr = call.request.lesFnrFraAuthToken()

                    hentForespoerselProducer.publish(kontekstId, request, arbeidsgiverFnr)

                    val resultatJson = redisPoller.hent(kontekstId)

                    sikkerLogger.info("Hentet forespørsel: $resultatJson")

                    val resultat = resultatJson.success?.fromJson(HentForespoerselResultat.serializer())
                    if (resultat != null) {
                        respond(HttpStatusCode.Created, resultat.toResponse(), HentForespoerselResponse.serializer())
                    } else {
                        val feilmelding = resultatJson.failure?.fromJson(String.serializer()) ?: "Teknisk feil, prøv igjen senere."
                        val response =
                            ResultJson(
                                failure = feilmelding.toJson(),
                            )
                        respond(HttpStatusCode.ServiceUnavailable, response, ResultJson.serializer())
                    }
                } catch (e: ManglerAltinnRettigheterException) {
                    val response =
                        ResultJson(
                            failure = "Du har ikke rettigheter for organisasjon.".toJson(),
                        )
                    respondForbidden(response, ResultJson.serializer())
                } catch (_: RedisPollerTimeoutException) {
                    logger.info("Fikk timeout for ${request.uuid}")
                    val response =
                        ResultJson(
                            failure = RedisTimeoutResponse(request.uuid).toJson(RedisTimeoutResponse.serializer()),
                        )
                    respondInternalServerError(response, ResultJson.serializer())
                }
            }.onFailure {
                logger.error("Klarte ikke lese request.", it)
                val response =
                    ResultJson(
                        failure = "Mangler forespørsel-ID for å hente forespørsel.".toJson(),
                    )
                respondBadRequest(response, ResultJson.serializer())
            }
        }
    }
}

private fun HentForespoerselResultat.toResponse(): HentForespoerselResponse =
    HentForespoerselResponse(
        navn = sykmeldtNavn,
        innsenderNavn = avsenderNavn,
        orgNavn = orgNavn,
        identitetsnummer = forespoersel.fnr.verdi,
        orgnrUnderenhet = forespoersel.orgnr.verdi,
        fravaersperioder = forespoersel.sykmeldingsperioder,
        egenmeldingsperioder = forespoersel.egenmeldingsperioder,
        bestemmendeFravaersdag = forespoersel.forslagBestemmendeFravaersdag(),
        eksternBestemmendeFravaersdag = forespoersel.eksternBestemmendeFravaersdag(),
        bruttoinntekt = inntekt?.gjennomsnitt(),
        tidligereinntekter = inntekt?.maanedOversikt.orEmpty(),
        forespurtData = forespoersel.forespurtData,
        erBesvart = forespoersel.erBesvart,
        feilReport =
            if (feil.isEmpty()) {
                null
            } else {
                FeilReport(
                    feil =
                        feil
                            .map {
                                Feilmelding(
                                    melding = it.value,
                                    status = null,
                                    datafelt = it.key,
                                )
                            }.toMutableList(),
                )
            },
    )
