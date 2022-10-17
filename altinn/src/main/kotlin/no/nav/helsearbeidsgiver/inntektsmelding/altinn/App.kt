package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import no.nav.helsearbeidsgiver.altinn.AltinnClient

fun main() {
    AltinnLøser(
        AltinnClient(
            url = "ALTINN_URL".fromEnv(),
            serviceCode = "ALTINN_SERVICE_CODE".fromEnv(),
            apiGwApiKey = "ALTINN_API_GW_API_KEY".fromEnv(),
            altinnApiKey = "ALTINN_API_KEY".fromEnv()
        )
    )
}

private fun String.fromEnv(): String =
    System.getenv(this) ?: throw IllegalStateException("Environment missing variable '$this'.")
