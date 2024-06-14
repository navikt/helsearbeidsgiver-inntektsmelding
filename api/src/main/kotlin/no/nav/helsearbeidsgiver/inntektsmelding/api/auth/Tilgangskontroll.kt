package no.nav.helsearbeidsgiver.inntektsmelding.api.auth

import io.ktor.server.request.ApplicationRequest
import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.helsearbeidsgiver.utils.json.fromJson
import java.util.UUID

class Tilgangskontroll(
    private val tilgangProducer: TilgangProducer,
    private val cache: LocalCache<Tilgang>,
    private val redisPoller: RedisPoller
) {
    fun validerTilgangTilForespoersel(
        request: ApplicationRequest,
        forespoerselId: UUID
    ) {
        validerTilgang(request, forespoerselId.toString()) { clientId, fnr ->
            tilgangProducer.publishForespoerselId(clientId, fnr, forespoerselId)
        }
    }

    fun validerTilgangTilOrg(
        request: ApplicationRequest,
        orgnr: String
    ) {
        validerTilgang(request, orgnr) { clientId, fnr ->
            tilgangProducer.publishOrgnr(clientId, fnr, orgnr)
        }
    }

    private fun validerTilgang(
        request: ApplicationRequest,
        cacheKeyPostfix: String,
        publish: (UUID, String) -> Unit
    ) {
        val clientId = UUID.randomUUID()
        val innloggerFnr = request.lesFnrFraAuthToken()

        val tilgang = runBlocking {
            cache.get("$innloggerFnr:$cacheKeyPostfix") {
                logger.info("Fant ikke tilgang i cache, ber om tilgangskontroll.")

                publish(clientId, innloggerFnr)

                val resultat = redisPoller.hent(clientId)
                    .fromJson(TilgangResultat.serializer())

                resultat.tilgang ?: throw ManglerAltinnRettigheterException()
            }
        }

        if (tilgang != Tilgang.HAR_TILGANG) {
            logger.warn("Kall for ID '$cacheKeyPostfix' har ikke tilgang.")
            throw ManglerAltinnRettigheterException()
        }
    }
}
