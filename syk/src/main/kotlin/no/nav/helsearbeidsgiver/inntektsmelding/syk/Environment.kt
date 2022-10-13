package no.nav.helsearbeidsgiver.inntektsmelding.syk

import no.nav.helsearbeidsgiver.felles.getEnvVar

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
