package no.nav.helsearbeidsgiver.inntektsmelding.aareg

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv(),
        aaregUrl = getEnvVar(
            "AAREG_URL",
            "https://localhost"
        )
    )
}

data class Environment(
    val raw: Map<String, String>,
    val aaregUrl: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Environment: Missing required variable \"$varName\"")
