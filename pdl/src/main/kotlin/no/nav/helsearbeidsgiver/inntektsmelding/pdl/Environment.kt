package no.nav.helsearbeidsgiver.inntektsmelding.pdl

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv(),
        pdlUrl = getEnvVar(
            "PDL_URL",
            "https://helsearbeidsgiver-proxy.dev-fss-pub.nais.io/pdl"
        ),
        wellknownUrl = getEnvVar(
            "AZURE_WELL_KNOWN_URL",
            "https://login.microsoftonline.com/62366534-1ec3-4962-8869-9b5535279d0b/v2.0/.well-known/openid-configuration"
        ),
        tokenEndpointUrl = getEnvVar(
            "AZURE_TOKEN_ENDPOINT_URL",
            "https://login.microsoftonline.com/62366534-1ec3-4962-8869-9b5535279d0b/oauth2/v2.0/token"
        ),
        scope = getEnvVar(
            "PROXY_SCOPE",
            "api://5ccfebdd-40b0-424b-9306-3383bd0febd7/.default"
        ),
        azureAppClientID = getEnvVar("AZURE_APP_CLIENT_ID"),
        azureAppClientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
        azureAppJwk = getEnvVar("AZURE_APP_JWK")
    )
}

data class Environment(
    val raw: Map<String, String>,
    val pdlUrl: String,
    val wellknownUrl: String,
    val tokenEndpointUrl: String,
    val scope: String,
    val azureAppClientID: String,
    val azureAppClientSecret: String,
    val azureAppJwk: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Environment: Missing required variable \"$varName\"")
