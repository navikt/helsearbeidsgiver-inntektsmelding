package no.nav.helsearbeidsgiver.inntektsmelding.api.auth

import io.ktor.server.request.ApplicationRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Tilgang
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class Tilgangskontroll(
    private val producer: Producer,
    private val cache: LocalCache<Tilgang>,
    redisConnection: RedisConnection,
) {
    private val redisPollerForespoersel = RedisStore(redisConnection, RedisPrefix.TilgangForespoersel).let(::RedisPoller)
    private val redisPollerOrg = RedisStore(redisConnection, RedisPrefix.TilgangOrg).let(::RedisPoller)

    fun validerTilgangTilForespoersel(
        request: ApplicationRequest,
        forespoerselId: UUID,
    ) {
        validerTilgang(redisPollerForespoersel, request, forespoerselId.toString()) { kontekstId, fnr ->
            producer.send(
                EventName.TILGANG_FORESPOERSEL_REQUESTED,
                kontekstId,
                fnr,
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            )
        }
    }

    fun validerTilgangTilOrg(
        request: ApplicationRequest,
        orgnr: Orgnr,
    ) {
        validerTilgang(redisPollerOrg, request, orgnr.verdi) { kontekstId, fnr ->
            producer.send(
                EventName.TILGANG_ORG_REQUESTED,
                kontekstId,
                fnr,
                Key.ORGNR_UNDERENHET to orgnr.toJson(),
            )
        }
    }

    private fun validerTilgang(
        redisPoller: RedisPoller,
        request: ApplicationRequest,
        cacheKeyPostfix: String,
        publish: (UUID, Fnr) -> Unit,
    ) {
        val kontekstId = UUID.randomUUID()
        val innloggerFnr = request.lesFnrFraAuthToken()

        val tilgang =
            runBlocking {
                cache.getOrPut("$innloggerFnr:$cacheKeyPostfix") {
                    logger.info("Fant ikke tilgang i cache, ber om tilgangskontroll.")

                    publish(kontekstId, innloggerFnr)

                    val tilgang =
                        redisPoller
                            .hent(kontekstId)
                            .success
                            ?.fromJson(Tilgang.serializer())

                    tilgang ?: throw ManglerAltinnRettigheterException()
                }
            }

        if (tilgang != Tilgang.HAR_TILGANG) {
            logger.warn("Kall for ID '$cacheKeyPostfix' har ikke tilgang.")
            throw ManglerAltinnRettigheterException()
        }
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
