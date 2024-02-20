package no.nav.helsearbeidsgiver.inntektsmelding.api.auth

import io.ktor.server.request.ApplicationRequest
import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangData
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
        validerTilgang(request, forespoerselId) { fnr ->
            tilgangProducer.publishForespoerselId(forespoerselId, fnr)
        }
    }

    fun validerTilgangTilOrg(
        request: ApplicationRequest,
        id: UUID,
        orgnr: String
    ) {
        validerTilgang(request, id) { fnr ->
            tilgangProducer.publishOrgnr(orgnr, fnr)
        }
    }

    private fun validerTilgang(
        request: ApplicationRequest,
        id: UUID,
        publish: (String) -> UUID
    ) {
        val innloggerFnr = request.lesFnrFraAuthToken()

        val tilgang = runBlocking {
            cache.get("$innloggerFnr:$id") {
                logger.info("Fant ikke tilgang i cache, ber om tilgangskontroll for '$id'.")

                val clientId = publish(innloggerFnr)

                val resultat = redisPoller.hent(clientId)
                    .fromJson(TilgangData.serializer())

                resultat.tilgang ?: throw ManglerAltinnRettigheterException()
            }
        }

        if (tilgang != Tilgang.HAR_TILGANG) {
            logger.warn("Kall for ID '$id' har ikke tilgang.")
            throw ManglerAltinnRettigheterException()
        }
    }
}
