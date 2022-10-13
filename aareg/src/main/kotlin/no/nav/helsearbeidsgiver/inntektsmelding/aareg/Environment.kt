package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import no.nav.helsearbeidsgiver.felles.getEnvVar

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv(),
        aaregUrl = getEnvVar("AAREG_URL"),
        scope = getEnvVar("PROXY_SCOPE"),
        azureWellKnownUrl = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
        azureTokenEndpointUrl = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
        azureAppClientID = getEnvVar("AZURE_APP_CLIENT_ID"),
        azureAppClientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
        azureAppJwk = getEnvVar("AZURE_APP_JWK")
    )
}

data class Environment(
    val raw: Map<String, String>,
    val aaregUrl: String,
    val scope: String,
    val azureWellKnownUrl: String,
    val azureTokenEndpointUrl: String,
    val azureAppClientID: String,
    val azureAppClientSecret: String,
    val azureAppJwk: String
)
