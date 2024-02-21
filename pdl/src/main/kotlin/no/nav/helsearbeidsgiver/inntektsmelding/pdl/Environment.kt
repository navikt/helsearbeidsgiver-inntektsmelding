package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import no.nav.helsearbeidsgiver.felles.getEnvVar
import no.nav.helsearbeidsgiver.felles.oauth2.AzureOAuth2Environment

fun setUpEnvironment(): Environment {
    return Environment(
        pdlUrl = getEnvVar("PDL_URL"),
        AzureOAuth2Environment(
            scope = getEnvVar("PROXY_SCOPE"),
            azureWellKnownUrl = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
            azureTokenEndpointUrl = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
            azureAppClientID = getEnvVar("AZURE_APP_CLIENT_ID"),
            azureAppClientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
            azureAppJwk = getEnvVar("AZURE_APP_JWK")
        )
    )
}

data class Environment(
    val pdlUrl: String,
    val azureOAuthEnvironment: AzureOAuth2Environment
)
