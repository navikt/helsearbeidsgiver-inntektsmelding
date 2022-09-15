package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.brreg.BrregClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("im-brreg")

fun main() {
    val environment = setUpEnvironment()
    val app = createApp(environment)
    app.start()
}

internal fun createApp(environment: Environment): RapidsConnection {
    logger.info("Starting RapidApplication...")
    val rapidsConnection = RapidApplication.create(environment.raw)
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }
    }
    logger.info("Starting BrregLøser...")
    BrregLøser(rapidsConnection, BrregClient(environment.brregUrl, httpClient))
    return rapidsConnection
}
