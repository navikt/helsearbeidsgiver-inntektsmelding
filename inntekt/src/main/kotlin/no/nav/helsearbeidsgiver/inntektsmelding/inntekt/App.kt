package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import io.ktor.client.HttpClient
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.tokenprovider.AccessTokenProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-inntekt")

fun main() {
    val environment = setUpEnvironment()
    val app = createApp(environment)
    app.start()
}

val tokenProvider = object : AccessTokenProvider {
    override fun getToken(): String {
        return "fake token"
    }
}

internal fun createApp(environment: Environment): RapidsConnection {
    logger.info("Starting RapidApplication...")
    val rapidsConnection = RapidApplication.create(environment.raw)
    logger.info("Starting...")
    val httpClient = HttpClient()
    val inntektKlient = InntektKlient(environment.inntektUrl, tokenProvider, httpClient)
    InntektLøser(rapidsConnection, inntektKlient)
    return rapidsConnection
}
