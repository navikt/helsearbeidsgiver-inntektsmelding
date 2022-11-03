package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.felles.fromEnv

fun main() {
    AltinnLøser(
        AltinnClient(
            url = "ALTINN_URL".fromEnv(),
            serviceCode = "ALTINN_SERVICE_CODE".fromEnv(),
            apiGwApiKey = "ALTINN_API_GW_API_KEY".fromEnv(),
            altinnApiKey = "ALTINN_API_KEY".fromEnv()
        )
    )
}
