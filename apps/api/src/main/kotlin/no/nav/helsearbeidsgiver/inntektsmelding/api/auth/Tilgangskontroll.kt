package no.nav.helsearbeidsgiver.inntektsmelding.api.auth

import io.ktor.server.request.ApplicationRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.resultat.tilgang.Tilgang
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class Tilgangskontroll(
    private val producer: Producer,
    private val cache: LocalCache<Tilgang>,
    redisConnection: RedisConnection,
) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    private val redisPollerForespoersel = RedisStore(redisConnection, RedisPrefix.TilgangForespoersel).let(::RedisPoller)
    private val redisPollerOrg = RedisStore(redisConnection, RedisPrefix.TilgangOrg).let(::RedisPoller)

    fun manglerTilgangTilForespoersel(
        request: ApplicationRequest,
        kontekstId: UUID,
        forespoerselId: UUID,
    ): Boolean? =
        manglerTilgang(redisPollerForespoersel, request, kontekstId, forespoerselId.toString()) { kontekstId, fnr ->
            producer.send(
                EventName.TILGANG_FORESPOERSEL_REQUESTED,
                kontekstId,
                fnr,
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            )
        }

    fun manglerTilgangTilOrg(
        request: ApplicationRequest,
        kontekstId: UUID,
        orgnr: Orgnr,
    ): Boolean? =
        manglerTilgang(redisPollerOrg, request, kontekstId, orgnr.verdi) { kontekstId, fnr ->
            producer.send(
                EventName.TILGANG_ORG_REQUESTED,
                kontekstId,
                fnr,
                Key.ORGNR_UNDERENHET to orgnr.toJson(),
            )
        }

    private fun manglerTilgang(
        redisPoller: RedisPoller,
        request: ApplicationRequest,
        kontekstId: UUID,
        cacheKeyPostfix: String,
        publish: (UUID, Fnr) -> Unit,
    ): Boolean? {
        val innloggerFnr = request.lesFnrFraAuthToken()

        val tilgang =
            runBlocking {
                cache.getOrPutOrNull("$innloggerFnr:$cacheKeyPostfix") {
                    "Fant ikke tilgang i cache, ber om tilgangskontroll.".also {
                        logger.info(it)
                        sikkerLogger.info(it)
                    }

                    publish(kontekstId, innloggerFnr)

                    redisPoller
                        .hent(kontekstId)
                        ?.success
                        ?.fromJson(Tilgang.serializer())
                }
            }

        val manglerTilgang = tilgang?.let { it == Tilgang.IKKE_TILGANG }
        if (manglerTilgang != null && manglerTilgang) {
            "Kall for '$cacheKeyPostfix' har ikke tilgang.".also {
                logger.warn(it)
                sikkerLogger.warn(it)
            }
        }

        return manglerTilgang
    }
}

private fun Producer.send(
    eventName: EventName,
    kontekstId: UUID,
    fnr: Fnr,
    dataField: Pair<Key, JsonElement>,
) {
    MdcUtils.withLogFields(
        Log.klasse(this),
        Log.event(eventName),
        Log.kontekstId(kontekstId),
    ) {
        send(
            key = fnr,
            message =
                mapOf(
                    Key.EVENT_NAME to eventName.toJson(),
                    Key.KONTEKST_ID to kontekstId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FNR to fnr.toJson(),
                            dataField,
                        ).toJson(),
                ),
        )
    }
}
