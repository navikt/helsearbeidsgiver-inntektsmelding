package no.nav.helsearbeidsgiver.inntektsmelding.api.auth

import io.ktor.server.request.ApplicationRequest
import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.felles.domene.Tilgang
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class Tilgangskontroll(
    private val tilgangProducer: TilgangProducer,
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
            tilgangProducer.publishForespoerselId(kontekstId, fnr, forespoerselId)
        }
    }

    fun validerTilgangTilOrg(
        request: ApplicationRequest,
        orgnr: String,
    ) {
        validerTilgang(redisPollerOrg, request, orgnr) { kontekstId, fnr ->
            tilgangProducer.publishOrgnr(kontekstId, fnr, orgnr)
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
                cache.get("$innloggerFnr:$cacheKeyPostfix") {
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
