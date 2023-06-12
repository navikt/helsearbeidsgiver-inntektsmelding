package no.nav.helsearbeidsgiver.inntektsmelding.api

import no.nav.helsearbeidsgiver.felles.app.LocalApp

fun main() {
    val env = LocalApp().setupEnvironment("im-api", 8081)

    startServer(env)
}
