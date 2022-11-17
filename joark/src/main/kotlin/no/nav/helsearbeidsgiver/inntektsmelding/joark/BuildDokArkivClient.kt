package no.nav.helsearbeidsgiver.inntektsmelding.joark

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig

fun buildDokArkivClient(environment: Environment): DokArkivClient {
    val tokenProvider = OAuth2ClientConfig(environment.azureOAuthEnvironment)
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }
    }
    return DokArkivClient(environment.dokarkivUrl, tokenProvider, httpClient)
}
