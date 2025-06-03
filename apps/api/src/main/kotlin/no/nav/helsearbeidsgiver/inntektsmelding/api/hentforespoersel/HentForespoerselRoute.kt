package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoersel

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingRequest
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.domene.HentForespoerselResultat
import no.nav.helsearbeidsgiver.felles.domene.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.kafka.Producer
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
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
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

fun Route.hentForespoersel(
    producer: Producer,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val redisPoller = RedisStore(redisConnection, RedisPrefix.HentForespoersel).let(::RedisPoller)

    post(Routes.HENT_FORESPOERSEL_GAMMEL) {
        val kontekstId = UUID.randomUUID()

        Metrics.hentForespoerselEndpoint.recordTime(Route::hentForespoersel) {
            runCatching {
                receive(HentForespoerselRequest.serializer())
            }.onSuccess { request ->
                logger.info("Henter data for uuid: ${request.uuid}")
                try {
                    tilgangskontroll.validerTilgangTilForespoersel(call.request, request.uuid)

                    val arbeidsgiverFnr = call.request.lesFnrFraAuthToken()

                    producer.sendRequestEvent(kontekstId, request.uuid, arbeidsgiverFnr)

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

    get(Routes.HENT_FORESPOERSEL) {
        val kontekstId = UUID.randomUUID()

        val forespoerselId =
            call.parameters["forespoerselId"]
                ?.runCatching(UUID::fromString)
                ?.getOrNull()

        val (statusCode, response) =
            if (forespoerselId == null) {
                val feilmelding = "Ugyldig parameter: '${call.parameters["forespoerselId"]}'."

                logger.error(feilmelding)
                sikkerLogger.error(feilmelding)

                HttpStatusCode.BadRequest to feilmelding.toJson()
            } else {
                MdcUtils.withLogFields(
                    Log.apiRoute(Routes.HENT_FORESPOERSEL),
                    Log.kontekstId(kontekstId),
                    Log.forespoerselId(forespoerselId),
                ) {
                    hentForespoersel(
                        tilgangskontroll = tilgangskontroll,
                        producer = producer,
                        redisPoller = redisPoller,
                        request = call.request,
                        kontekstId = kontekstId,
                        forespoerselId = forespoerselId,
                    )
                }
            }

        respond(statusCode, response, JsonElement.serializer())
    }
}

private suspend fun hentForespoersel(
    tilgangskontroll: Tilgangskontroll,
    producer: Producer,
    redisPoller: RedisPoller,
    request: RoutingRequest,
    kontekstId: UUID,
    forespoerselId: UUID,
): Pair<HttpStatusCode, JsonElement> {
    "Henter forespørsel.".also {
        logger.info(it)
        sikkerLogger.info(it)
    }

    try {
        tilgangskontroll.validerTilgangTilForespoersel(request, forespoerselId)
    } catch (_: ManglerAltinnRettigheterException) {
        return HttpStatusCode.Forbidden to "Mangler rettigheter for organisasjon.".toJson()
    }

    val arbeidsgiverFnr = request.lesFnrFraAuthToken()

    producer.sendRequestEvent(
        kontekstId = kontekstId,
        forespoerselId = forespoerselId,
        arbeidsgiverFnr = arbeidsgiverFnr,
    )

    val resultatJson =
        try {
            redisPoller.hent(kontekstId)
        } catch (_: RedisPollerTimeoutException) {
            "Klarte ikke hente forespørsel pga. Redis-timeout.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }

            return HttpStatusCode.InternalServerError to Tekst.REDIS_TIMEOUT_FEILMELDING.toJson()
        }

    val resultat = resultatJson.success?.fromJson(HentForespoerselResultat.serializer())

    return if (resultat != null) {
        val response = resultat.toResponse().toJson(HentForespoerselResponse.serializer())

        "Forespørsel hentet OK.".also {
            logger.info(it)
            sikkerLogger.info("$it\n${response.toPretty()}")
        }

        HttpStatusCode.OK to response
    } else {
        val feilmelding =
            resultatJson.failure
                ?.fromJson(String.serializer())
                ?: Tekst.TEKNISK_FEIL_FORBIGAAENDE

        "Klarte ikke hente forespørsel.".also {
            logger.info(it)
            sikkerLogger.info("$it Feilmelding: '$feilmelding'.")
        }

        HttpStatusCode.InternalServerError to feilmelding.toJson()
    }
}

private fun Producer.sendRequestEvent(
    kontekstId: UUID,
    forespoerselId: UUID,
    arbeidsgiverFnr: Fnr,
) {
    send(
        key = forespoerselId,
        message =
            mapOf(
                Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                        Key.ARBEIDSGIVER_FNR to arbeidsgiverFnr.toJson(),
                    ).toJson(),
            ),
    )
}

private fun HentForespoerselResultat.toResponse(): HentForespoerselResponse =
    HentForespoerselResponse(
        sykmeldt =
            Sykmeldt(
                fnr = forespoersel.fnr,
                navn = sykmeldtNavn,
            ),
        avsender =
            Avsender(
                orgnr = forespoersel.orgnr,
                orgNavn = orgNavn,
                navn = avsenderNavn,
            ),
        egenmeldingsperioder = forespoersel.egenmeldingsperioder,
        sykmeldingsperioder = forespoersel.sykmeldingsperioder,
        bestemmendeFravaersdag = forespoersel.forslagBestemmendeFravaersdag(),
        eksternInntektsdato = forespoersel.eksternInntektsdato(),
        inntekt =
            inntekt?.let {
                Inntekt(
                    gjennomsnitt = it.gjennomsnitt(),
                    historikk = it,
                )
            },
        forespurtData = forespoersel.forespurtData,
        erBesvart = forespoersel.erBesvart,
        // utdaterte felt
        navn = sykmeldtNavn,
        innsenderNavn = avsenderNavn,
        orgNavn = orgNavn,
        identitetsnummer = forespoersel.fnr.verdi,
        orgnrUnderenhet = forespoersel.orgnr.verdi,
        fravaersperioder = forespoersel.sykmeldingsperioder,
        eksternBestemmendeFravaersdag = forespoersel.eksternInntektsdato(),
        bruttoinntekt = inntekt?.gjennomsnitt(),
        tidligereinntekter = inntekt?.map { InntektPerMaaned(it.key, it.value) }.orEmpty(),
    )
