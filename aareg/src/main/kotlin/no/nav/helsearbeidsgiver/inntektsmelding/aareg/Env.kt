package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import no.nav.helsearbeidsgiver.felles.utils.fromEnv
import no.nav.helsearbeidsgiver.tokenprovider.OAuth2Environment

object Env {
    val aaregUrl = "AAREG_URL".fromEnv()

    val oauth2Environment =
        OAuth2Environment(
            scope = "AAREG_SCOPE".fromEnv(),
            wellKnownUrl = "AZURE_APP_WELL_KNOWN_URL".fromEnv(),
            tokenEndpointUrl = "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT".fromEnv(),
            clientId = "AZURE_APP_CLIENT_ID".fromEnv(),
            clientSecret = "AZURE_APP_CLIENT_SECRET".fromEnv(),
            clientJwk = "AZURE_APP_JWK".fromEnv(),
        )
}
