package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helsearbeidsgiver.felles.fromEnv
import no.nav.helsearbeidsgiver.felles.oauth2.AzureOAuth2Environment

object Env {
    val redisUrl = "REDIS_URL".fromEnv()
    val linkUrl = "LINK_URL".fromEnv()
    val notifikasjonUrl = "ARBEIDSGIVER_NOTIFIKASJON_API_URL".fromEnv()
    val azureOAuthEnvironment = AzureOAuth2Environment(
        scope = "ARBEIDSGIVER_NOTIFIKASJON_SCOPE".fromEnv(),
        azureWellKnownUrl = "AZURE_APP_WELL_KNOWN_URL".fromEnv(),
        azureTokenEndpointUrl = "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT".fromEnv(),
        azureAppClientID = "AZURE_APP_CLIENT_ID".fromEnv(),
        azureAppClientSecret = "AZURE_APP_CLIENT_SECRET".fromEnv(),
        azureAppJwk = "AZURE_APP_JWK".fromEnv()
    )
}
