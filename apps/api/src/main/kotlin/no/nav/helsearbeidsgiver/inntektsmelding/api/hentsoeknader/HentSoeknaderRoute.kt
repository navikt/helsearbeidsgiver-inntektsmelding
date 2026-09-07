package no.nav.helsearbeidsgiver.inntektsmelding.api.hentsoeknader

import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.serializer
import no.nav.hag.simba.kontrakt.resultat.soeknad.hentSoeknaderResultatSerializer
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.validerTilgangOrgnrOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.hentResultatFraRedisOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.readRequestOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.util.UUID

fun Route.hentSoeknaderRoute(
    producer: Producer,
    tilgangskontroll: Tilgangskontroll,
    redisConnection: RedisConnection,
) {
    val redisPoller = RedisStore(redisConnection, RedisPrefix.HentSoeknader).let(::RedisPoller)

    post(Routes.HENT_SOEKNADER) {
        val kontekstId = UUID.randomUUID()

        MdcUtils.withLogFields(
            Log.apiRoute(Routes.HENT_SOEKNADER),
            Log.kontekstId(kontekstId),
        ) {
            readRequestOrError(
                kontekstId,
                HentSoeknaderRequest.serializer(),
            ) { request ->
                validerTilgangOrgnrOrError(tilgangskontroll, kontekstId, request.orgnr) {
                    "Henter søknader.".also {
                        logger.info(it)
                        sikkerLogger.info(it)
                    }

                    producer.sendRequestEvent(kontekstId, request)

                    hentResultatFraRedisOrError(
                        redisPoller = redisPoller,
                        kontekstId = kontekstId,
                        logOnFailure = "Klarte ikke hente søknader pga. feil.",
                        successSerializer = hentSoeknaderResultatSerializer,
                    ) { success ->
                        "Hentet ${success.first.size} forespørsler, ${success.second.size} søknader og ${success.third.size} behandlingsdagssøknader.".also {
                            logger.info(it)
                            sikkerLogger.info(it)
                        }

                        val response =
                            HentSoeknaderResponse(
                                success.first.map(::ForespoerselResponse),
                                success.second.map(::SoeknadArbeidstakerResponse),
                                success.third.map(::SoeknadBehandlingsdagerResponse),
                            )

                        respondOk(response, HentSoeknaderResponse.serializer())
                    }
                }
            }
        }
    }
}

private fun Producer.sendRequestEvent(
    kontekstId: UUID,
    request: HentSoeknaderRequest,
) {
    send(
        key = request.sykmeldtFnr,
        message =
            mapOf(
                Key.EVENT_NAME to EventName.REQUEST_HENT_SOEKNAD_LISTE.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.ORGNR_UNDERENHET to request.orgnr.toJson(),
                        Key.SYKMELDT_FNR to request.sykmeldtFnr.toJson(),
                        Key.ER_BEHANDLINGSDAGER to request.erBehandlingsdager.toJson(Boolean.serializer()),
                    ).toJson(),
            ),
    )
}
