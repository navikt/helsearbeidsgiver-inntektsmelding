package no.nav.helsearbeidsgiver.inntektsmelding.preutfylt

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.app.LocalApp

fun main() {
    val env = LocalApp().setupEnvironment("im-preutfylt", 8084)
    val rapid = RapidApplication.create(env)
    HentPreutfyltLøser(rapid)
    rapid.start()
}
