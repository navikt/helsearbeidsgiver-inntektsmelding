package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.brreg.BrregClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-brreg")

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
    logger.info("Starter Rapid")
    val brregUrl = System.getenv().get("ENHETSREGISTERET_URL")!!
    RapidApplication.create(System.getenv()).apply {
        logger.info("Starter BrregLøser")
        BrregLøser(this, BrregClient(brregUrl, httpClient))
    }.start()
}
