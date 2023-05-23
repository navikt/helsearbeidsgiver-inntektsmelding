package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.utils.json.jsonIgnoreUnknown
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

val sikkerLogger = sikkerLogger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createInntekt(buildInntektKlient())
        .start()
}

fun RapidsConnection.createInntekt(inntektKlient: InntektKlient): RapidsConnection =
    also {
        sikkerLogger.info("Starter InntektLøser...")
        InntektLøser(this, inntektKlient)
    }

fun buildInntektKlient(): InntektKlient =
    InntektKlient(
        baseUrl = Env.inntektUrl,
        stsClient = OAuth2ClientConfig(Env.azureOAuthEnvironment),
        httpClient = buildClient()
    )

fun buildClient(): HttpClient =
    HttpClient {
        install(ContentNegotiation) {
            json(jsonIgnoreUnknown)
        }
    }
