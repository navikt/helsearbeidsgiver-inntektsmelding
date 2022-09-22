package no.nav.helsearbeidsgiver.inntektsmelding.pdl

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv(),
        pdlUrl = getEnvVar(
            "PDL_URL",
            "https://helsearbeidsgiver-proxy.dev-fss-pub.nais.io/pdl"
        ),
        scope = getEnvVar(
            "PROXY_SCOPE",
            "api://5ccfebdd-40b0-424b-9306-3383bd0febd7/.default"
        ),
        azureWellknownUrl = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
        azureTokenEndpointUrl = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
        azureAppClientID = getEnvVar("AZURE_APP_CLIENT_ID"),
        azureAppClientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
        azureAppJwk = getEnvVar("AZURE_APP_JWK")
    )
}

data class Environment(
    val raw: Map<String, String>,
    val pdlUrl: String,
    val scope: String,
    val azureWellknownUrl: String,
    val azureTokenEndpointUrl: String,
    val azureAppClientID: String,
    val azureAppClientSecret: String,
    val azureAppJwk: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Environment: Missing required variable \"$varName\"")
