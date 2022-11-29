package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helsearbeidsgiver.felles.getEnvVar

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv(),
        notifikasjonUrl = getEnvVar(
            "NOTIFIKASJON_URL",
            "https://localhost"
        )
    )
}

data class Environment(
    val raw: Map<String, String>,
    val notifikasjonUrl: String
)
