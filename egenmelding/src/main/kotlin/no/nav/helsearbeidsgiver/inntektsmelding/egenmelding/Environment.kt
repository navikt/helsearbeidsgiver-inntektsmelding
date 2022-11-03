package no.nav.helsearbeidsgiver.inntektsmelding.egenmelding

import no.nav.helsearbeidsgiver.felles.getEnvVar

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv(),
        egenmeldingUrl = getEnvVar(
            "EGENMELDING_URL",
            "https://localhost"
        )
    )
}

data class Environment(
    val raw: Map<String, String>,
    val egenmeldingUrl: String
)
