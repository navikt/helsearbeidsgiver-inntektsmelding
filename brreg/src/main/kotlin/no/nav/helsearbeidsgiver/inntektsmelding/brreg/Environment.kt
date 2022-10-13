package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import no.nav.helsearbeidsgiver.felles.getEnvVar

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
