package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.app.LocalApp

fun main() {
    val env = LocalApp().setupEnvironment("im-innsending", 8088)

    RapidApplication
        .create(env)
        .createInnsending(buildRedisStore(setUpEnvironment()))
        .start()
}
