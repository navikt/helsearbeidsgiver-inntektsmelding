package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import no.nav.helsearbeidsgiver.felles.fromEnv
import no.nav.helsearbeidsgiver.tokenprovider.OAuth2Environment

object Env {
    val spinnUrl = "SPINN_API_URL".fromEnv()

    val oauth2Environment =
        OAuth2Environment(
            scope = "SPINN_SCOPE".fromEnv(),
            wellKnownUrl = "AZURE_APP_WELL_KNOWN_URL".fromEnv(),
            tokenEndpointUrl = "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT".fromEnv(),
            clientId = "AZURE_APP_CLIENT_ID".fromEnv(),
            clientSecret = "AZURE_APP_CLIENT_SECRET".fromEnv(),
            clientJwk = "AZURE_APP_JWK".fromEnv(),
        )

    val redisUrl = "REDIS_URL".fromEnv()
}
