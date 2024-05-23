package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import no.nav.helsearbeidsgiver.felles.getEnvVar
import no.nav.helsearbeidsgiver.tokenprovider.OAuth2Environment

fun setUpEnvironment(): Environment {
    return Environment(
        pdlUrl = getEnvVar("PDL_URL"),
        oauth2Environment = OAuth2Environment(
            scope = getEnvVar("PDL_SCOPE"),
            wellKnownUrl = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
            tokenEndpointUrl = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
            clientId = getEnvVar("AZURE_APP_CLIENT_ID"),
            clientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
            clientJwk = getEnvVar("AZURE_APP_JWK")
        )
    )
}

data class Environment(
    val pdlUrl: String,
    val oauth2Environment: OAuth2Environment
)
