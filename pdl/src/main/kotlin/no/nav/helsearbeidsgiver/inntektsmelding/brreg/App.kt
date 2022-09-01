package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.pdl.PdlClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-pdl")

fun main() {
    val environment = setUpEnvironment()
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }
    }
    // val oauth2TokenProvider = OAuth2TokenProvider()
    // val pdlClient = PdlClient(environment.pdlUrl, { oauth2TokenProvider.getToken() }, httpClient)

    RapidApplication.create(environment.raw).apply {
        PdlLøser(this)
    }.start()
}
