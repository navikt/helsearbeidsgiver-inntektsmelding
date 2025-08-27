package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoersel

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingRequest
import io.ktor.server.routing.get
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.domene.HentForespoerselResultat
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.kafka.Producer
import no.nav.helsearbeidsgiver.felles.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.gjennomsnitt
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.lesFnrFraAuthToken
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respond
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
        erBegrenset = forespoersel.erBegrenset,
    )
