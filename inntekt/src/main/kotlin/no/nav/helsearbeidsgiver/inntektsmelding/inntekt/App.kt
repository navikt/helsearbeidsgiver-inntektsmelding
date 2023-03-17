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
    RapidApplication
        .create(System.getenv())
        .createInntekt(buildInntektKlient(setUpEnvironment()))
        .start()
}

fun RapidsConnection.createInntekt(inntektKlient: InntektKlient): RapidsConnection {
    sikkerlogg.info("Starter InntektLøser...")
    InntektLøser(this, inntektKlient)
    return this
}

fun buildInntektKlient(environment: Environment): InntektKlient {
    val tokenProvider = OAuth2ClientConfig(environment.azureOAuthEnvironment)
    return InntektKlient(environment.inntektUrl, tokenProvider, buildClient())
}

fun buildClient(): HttpClient {
    return HttpClient {
        install(ContentNegotiation) {
            json(jsonIgnoreUnknown)
        }
    }
}
