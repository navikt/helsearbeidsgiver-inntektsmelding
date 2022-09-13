package no.nav.helsearbeidsgiver.inntektsmelding.brreg

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv(),
        brregUrl = getEnvVar(
            "ENHETSREGISTERET_URL",
            "https://data.brreg.no/enhetsregisteret/api/underenheter/"
        )
    )
}

data class Environment(
    val raw: Map<String, String>,
    val brregUrl: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Environment: Missing required variable \"$varName\"")
