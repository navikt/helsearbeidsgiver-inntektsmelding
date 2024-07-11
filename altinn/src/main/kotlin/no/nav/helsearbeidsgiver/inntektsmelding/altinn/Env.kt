package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import no.nav.helsearbeidsgiver.felles.fromEnv

object Env {
    val url = "ALTINN_URL".fromEnv()
    val serviceCode = "ALTINN_SERVICE_CODE".fromEnv()
    val altinnApiKey = "ALTINN_API_KEY".fromEnv()

    object Maskinporten {
        val endpoint: String = "MASKINPORTEN_TOKEN_ENDPOINT".fromEnv()
        val issuer: String = "MASKINPORTEN_ISSUER".fromEnv()
        val clientJwk: String = "MASKINPORTEN_CLIENT_JWK".fromEnv()
        val clientId: String = "MASKINPORTEN_CLIENT_ID".fromEnv()
        val altinnScope: String = "ALTINN_SCOPE".fromEnv()
    }
}
