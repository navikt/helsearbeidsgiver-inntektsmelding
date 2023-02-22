package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv()

    )
}

data class Environment(
    val raw: Map<String, String>
)
