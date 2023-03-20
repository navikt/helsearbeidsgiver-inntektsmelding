package no.nav.helsearbeidsgiver.inntektsmelding.api

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.app.LocalApp

fun main() {
    val env = LocalApp().getLocalEnvironment("im-api", 8081)

    RapidApplication.create(env)
        .also(::startServer).start()
}
