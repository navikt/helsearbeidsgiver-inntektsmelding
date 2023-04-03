package no.nav.helsearbeidsgiver.inntektsmelding.api.authorization

import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.altinn.CacheConfig
import no.nav.helsearbeidsgiver.inntektsmelding.api.Env
import kotlin.time.Duration.Companion.minutes

interface AltinnAuthorizer {
    /**
     * Sjekker om den angitte identiteten har rettighet til å se refusjoner for den angitte arbeidsgiverId
     * En arbeidsgiverId kan være en virksomhet, en hovedenhet, et identitetsnummer på en privatperson eller et
     * organisasjonsledd.
     */
    fun hasAccess(identitetsnummer: String, arbeidsgiverId: String): Boolean
}

/**
 * Standard Altinn Authorizer som sjekker at
 *  * Den angitte brukeren har rettigheter til den angitte arbeidsgiverIDen
 *  * Den angitte arbeidsgiver IDen er en underenhet (virksomhet)
 */
class DefaultAltinnAuthorizer() : AltinnAuthorizer {

    val altinnClient: AltinnClient
    init {
        altinnClient = AltinnClient(
            url = Env.Altinn.url,
            serviceCode = Env.Altinn.serviceCode,
            apiGwApiKey = Env.Altinn.apiGwApiKey,
            altinnApiKey = Env.Altinn.altinnApiKey,
            cacheConfig = CacheConfig(60.minutes, 100)
        )
    }
    override fun hasAccess(identitetsnummer: String, arbeidsgiverId: String): Boolean {
        return runBlocking {
            altinnClient.harRettighetForOrganisasjon(identitetsnummer, arbeidsgiverId)
        }
    }
}
