package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helsearbeidsgiver.felles.getEnvVar

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv(),
        Dokarkiv(getEnvVar("DOKARKIV_URL", ""))
    )
}

data class Environment(
    val raw: Map<String, String>,
    val dokarkiv: Dokarkiv
)

data class Dokarkiv(
    val url: String
)
