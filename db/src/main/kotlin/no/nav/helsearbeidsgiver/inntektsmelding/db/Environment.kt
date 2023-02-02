package no.nav.helsearbeidsgiver.inntektsmelding.db

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv()
    )
}

data class Environment(
    val raw: Map<String, String>
)
