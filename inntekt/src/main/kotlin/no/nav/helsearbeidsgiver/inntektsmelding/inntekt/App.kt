package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.json.jsonIgnoreUnknown
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig
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
    logger.info("Starter Inntekt rapid...")
    sikkerlogg.info("Starter Inntekt rapid...")
    val rapidsConnection = RapidApplication.create(environment.raw)
    sikkerlogg.info("Starter tokenprovider...")
    val tokenProvider = OAuth2ClientConfig(environment.azureOAuthEnvironment)
    sikkerlogg.info("Starter InntektKlient...")
    val inntektKlient = InntektKlient(environment.inntektUrl, tokenProvider, buildClient())
    sikkerlogg.info("Starter InntektLøser...")
    InntektLøser(rapidsConnection, inntektKlient)
    return rapidsConnection
}

fun buildClient(): HttpClient {
    return HttpClient {
        install(ContentNegotiation) {
            json(jsonIgnoreUnknown)
        }
    }
}
