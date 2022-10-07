package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv(),
        inntektUrl = getEnvVar(
            "INNTEKT_URL",
            "https://localhost"
        )
    )
}

data class Environment(
    val raw: Map<String, String>,
    val inntektUrl: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Environment: Missing required variable \"$varName\"")
