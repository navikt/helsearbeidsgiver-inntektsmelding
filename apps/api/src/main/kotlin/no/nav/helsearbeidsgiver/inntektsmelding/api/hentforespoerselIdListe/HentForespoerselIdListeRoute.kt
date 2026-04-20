package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoerselIdListe

import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.MapSerializer
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.validerTilgangOrgnrOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.hentResultatFraRedisOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.readRequestOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
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
        val kontekstId = UUID.randomUUID()

        readRequestOrError(
            kontekstId,
            HentForespoerslerRequest.serializer(),
        ) {
            val vedtaksperiodeIdListe = it.vedtaksperiodeIdListe

            when {
                vedtaksperiodeIdListe.isEmpty() -> {
                    loggErrorSikkerOgUsikker("Kan ikke hente forespørsler for tom vedtaksperiode-ID-liste.")
                    respondError(ErrorResponse.Unknown(kontekstId))
                }

                vedtaksperiodeIdListe.size > MAKS_ANTALL_VEDTAKSPERIODE_IDER -> {
                    loggErrorSikkerOgUsikker(
                        "Stopper forsøk på å hente forespørsler for mer enn $MAKS_ANTALL_VEDTAKSPERIODE_IDER vedtaksperiode-ID-er på en gang.",
                    )
                    respondError(ErrorResponse.Unknown(kontekstId))
                }

                else -> {
                    hentForespoersler(producer, tilgangskontroll, redisPoller, kontekstId, vedtaksperiodeIdListe)
                }
            }
        }
    }
}

private suspend fun RoutingContext.hentForespoersler(
    producer: Producer,
    tilgangskontroll: Tilgangskontroll,
    redisPoller: RedisPoller,
    kontekstId: UUID,
    vedtaksperiodeIdListe: Set<UUID>,
) {
    loggInfoSikkerOgUsikker("Henter forespørsler for liste med vedtaksperiode-ID-er: $vedtaksperiodeIdListe")

    producer.sendRequestEvent(kontekstId, vedtaksperiodeIdListe)

    hentResultatFraRedisOrError(
        redisPoller = redisPoller,
        kontekstId = kontekstId,
        logOnFailure = "Klarte ikke hente forespørsler for liste med vedtaksperiode-ID-er pga. feil.",
        successSerializer = MapSerializer(UuidSerializer, Forespoersel.serializer()),
    ) { success ->
        "Hentet forespørsler for liste med vedtaksperiode-ID-er.".also {
            logger.info(it)
            sikkerLogger.info("$it\n$success")
        }

        val alleOrgnr = success.map { (_, forespoersel) -> forespoersel.orgnr }.toSet()
        val orgnr = alleOrgnr.firstOrNull()

        when {
            alleOrgnr.size > 1 -> {
                "Stopper forsøk på å hente forespørsler for vedtaksperioder, fordi de tilhører ulike arbeidsgivere.".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }
                respondError(ErrorResponse.Unknown(kontekstId))
            }

            // Dersom orgnr er 'null' betyr det at ingen forespørsler ble funnet.
            orgnr == null -> {
                respondOk(emptyList(), VedtaksperiodeIdForespoerselIdPar.serializer().list())
            }

            else -> {
                validerTilgangOrgnrOrError(tilgangskontroll, kontekstId, orgnr) {
                    val respons =
                        success.map { (id, forespoersel) ->
                            VedtaksperiodeIdForespoerselIdPar(
                                forespoerselId = id,
                                vedtaksperiodeId = forespoersel.vedtaksperiodeId,
                            )
                        }

                    respondOk(respons, VedtaksperiodeIdForespoerselIdPar.serializer().list())
                }
            }
        }
    }
}

private fun Producer.sendRequestEvent(
    kontekstId: UUID,
    vedtaksperiodeIdListe: Set<UUID>,
) {
    send(
        key = UUID.randomUUID(),
        message =
            mapOf(
                Key.EVENT_NAME to EventName.FORESPOERSLER_REQUESTED.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.VEDTAKSPERIODE_ID_LISTE to vedtaksperiodeIdListe.toJson(UuidSerializer),
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
    logger.error(loggMelding)
    if (throwable == null) {
        sikkerLogger.error(loggMelding)
    } else {
        sikkerLogger.error(loggMelding, throwable)
    }
}
