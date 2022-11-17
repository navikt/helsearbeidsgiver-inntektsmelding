package no.nav.helsearbeidsgiver.inntektsmelding.joark

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.felles.oauth2.AzureOAuth2Environment
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig

fun buildDokArkivClient(azureOAuthEnvironment: AzureOAuth2Environment): DokArkivClient {
    val tokenProvider = OAuth2ClientConfig(azureOAuthEnvironment)
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }
    }
    return DokArkivClient("http://localhost", tokenProvider, httpClient)
}
