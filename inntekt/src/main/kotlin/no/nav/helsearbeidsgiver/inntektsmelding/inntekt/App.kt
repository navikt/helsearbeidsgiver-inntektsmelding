package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-inntekt")

fun main() {
    val environment = setUpEnvironment()
    val app = createApp(environment)
    app.start()
}

internal fun createApp(environment: Environment): RapidsConnection {
    val rapidsConnection = RapidApplication.create(environment.raw)
    logger.info("Starter tokenprovider...")
    val tokenProvider = OAuth2ClientConfig(environment)
    logger.info("Starter InntektKlient...")
    val inntektKlient = InntektKlient(environment.inntektUrl, tokenProvider, buildClient())
    logger.info("Starting RapidApplication...")
    InntektLøser(rapidsConnection, inntektKlient)
    return rapidsConnection
}

fun buildClient(): HttpClient {
    return HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }
    }
}
