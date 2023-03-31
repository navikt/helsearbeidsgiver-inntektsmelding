package no.nav.helsearbeidsgiver.inntektsmelding.api.authorization

import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.altinn.AltinnClient

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
class DefaultAltinnAuthorizer(private val altinnClient: AltinnClient) : AltinnAuthorizer {
    override fun hasAccess(identitetsnummer: String, arbeidsgiverId: String): Boolean {
        return runBlocking {
            altinnClient.harRettighetForOrganisasjon(identitetsnummer, arbeidsgiverId)
        }
    }
}
