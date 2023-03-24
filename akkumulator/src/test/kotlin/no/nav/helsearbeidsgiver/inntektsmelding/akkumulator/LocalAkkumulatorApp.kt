package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.app.LocalApp
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.RedisStore

fun main() {
    val environment = LocalAkkumulatorApp().setupEnvironment("im-akkumulator", 8082)
    RapidApplication.create(environment)
        .createAkkumulator(RedisStore(environment["REDIS_URL"]!!))
        .start()
}

class LocalAkkumulatorApp : LocalApp() {
    override fun setupEnvironment(memberId: String, httpPort: Int): Map<String, String> {
        return mapOf(
            "localOverride" to "whatever"
        ).plus(
            super.setupEnvironment(memberId, httpPort)
        )
    }
}
