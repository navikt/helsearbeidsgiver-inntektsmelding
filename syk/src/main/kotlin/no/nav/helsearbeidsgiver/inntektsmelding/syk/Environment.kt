package no.nav.helsearbeidsgiver.inntektsmelding.syk

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv(),
        sykUrl = getEnvVar(
            "SYK_URL",
            "https://localhost"
        )
    )
}

data class Environment(
    val raw: Map<String, String>,
    val sykUrl: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Environment: Missing required variable \"$varName\"")
