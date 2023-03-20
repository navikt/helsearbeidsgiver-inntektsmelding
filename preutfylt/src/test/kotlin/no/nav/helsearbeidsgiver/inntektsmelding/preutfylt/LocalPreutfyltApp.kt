package no.nav.helsearbeidsgiver.inntektsmelding.preutfylt

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.app.LocalApp
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-integrasjon")

fun main() {
    val env = LocalApp().getLocalEnvironment("im-preutfylt", 8084)
    val rapid = RapidApplication.create(env)
    HentPreutfyltLøser(rapid)
    rapid.start()
}

class LocalPreutfyltApp
