package no.nav.helsearbeidsgiver.inntektsmelding.api

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.felles.app.LocalApp

fun main() {
    val env = LocalApp().setupEnvironment("im-api", 8081)
    val altinnClient = AltinnClient(
        "https://fakedings.altinn.dev.nav.no",
        "1234",
        "key123",
        "key123"
    )
    val rapidsConnection = RapidApplication.create(env)
    startServer(rapidsConnection)
}
