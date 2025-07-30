package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoerselIdListe

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tekst.TEKNISK_FEIL_FORBIGAAENDE
import no.nav.helsearbeidsgiver.felles.Tekst.UGYLDIG_REQUEST
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.VedtaksperiodeIdForespoerselIdPar
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.kafka.Producer
import no.nav.helsearbeidsgiver.felles.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.redis.RedisStore
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
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

const val MAKS_ANTALL_VEDTAKSPERIODE_IDER = 100

fun Route.hentForespoerselIdListe(
    producer: Producer,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val redisPoller = RedisStore(redisConnection, RedisPrefix.HentForespoerslerForVedtaksperiodeIdListe).let(::RedisPoller)

    post(Routes.HENT_FORESPOERSEL_ID_LISTE) {
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
                    hentForespoersler(producer, tilgangskontroll, redisPoller, request)
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

private suspend fun RoutingContext.hentForespoersler(
    producer: Producer,
    tilgangskontroll: Tilgangskontroll,
    redisPoller: RedisPoller,
    request: HentForespoerslerRequest,
) {
    loggInfoSikkerOgUsikker("Henter forespørsler for liste med vedtaksperiode-IDer: ${request.vedtaksperiodeIdListe}")

    val kontekstId = UUID.randomUUID()

    producer.sendRequestEvent(kontekstId, request)

    val resultatJson = redisPoller.hent(kontekstId)

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
                    orgnrSet.firstOrNull()?.also { orgnr -> tilgangskontroll.validerTilgangTilOrg(call.request, orgnr) }

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

private fun Producer.sendRequestEvent(
    kontekstId: UUID,
    request: HentForespoerslerRequest,
) {
    send(
        key = UUID.randomUUID(),
        message =
            mapOf(
                Key.EVENT_NAME to EventName.FORESPOERSLER_REQUESTED.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.VEDTAKSPERIODE_ID_LISTE to request.vedtaksperiodeIdListe.toJson(UuidSerializer),
                    ).toJson(),
            ),
    )
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
