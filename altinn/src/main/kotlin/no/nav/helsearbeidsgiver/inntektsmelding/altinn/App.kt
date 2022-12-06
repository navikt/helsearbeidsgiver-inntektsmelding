package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import no.nav.helsearbeidsgiver.altinn.AltinnClient

fun main() {
    AltinnLøser(
        AltinnClient(
            url = Env.url,
            serviceCode = Env.serviceCode,
            apiGwApiKey = Env.apiGwApiKey,
            altinnApiKey = Env.altinnApiKey
        )
    )
}
