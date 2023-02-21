package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import no.nav.helsearbeidsgiver.felles.getEnvVar
import no.nav.helsearbeidsgiver.felles.oauth2.AzureOAuth2Environment

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv()

    )
}

data class Environment(
    val raw: Map<String, String>
)
