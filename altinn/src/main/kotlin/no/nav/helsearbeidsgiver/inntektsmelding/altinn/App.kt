package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.altinn.AltinnClient

fun main() {
    RapidApplication
        .create(System.getenv())
        .createAltinn(buildAltinnClient())
        .start()
}

fun buildAltinnClient(): AltinnClient {
    return AltinnClient(
        url = Env.url,
        serviceCode = Env.serviceCode,
        apiGwApiKey = Env.apiGwApiKey,
        altinnApiKey = Env.altinnApiKey
    )
}

fun RapidsConnection.createAltinn(altinnClient: AltinnClient): RapidsConnection {
    TilgangskontrollLøser(this, altinnClient)
    return this
}
