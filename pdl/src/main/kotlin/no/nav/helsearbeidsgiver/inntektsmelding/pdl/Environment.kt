package no.nav.helsearbeidsgiver.inntektsmelding.pdl

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv(),
        pdlUrl = getEnvVar(
            "PDL_URL",
            "https://helsearbeidsgiver-proxy.dev-fss-pub.nais.io/pdl"
        )
    )
}

data class Environment(
    val raw: Map<String, String>,
    val pdlUrl: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Environment: Missing required variable \"$varName\"")
