package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.app.LocalApp

fun main() {
    val environment = LocalAkkumulatorApp().getLocalEnvironment("im-akkumulator", 8082)
    RapidApplication.create(environment)
        .createAkkumulator(RedisStore(environment["REDIS_URL"]!!))
        .start()
}

class LocalAkkumulatorApp : LocalApp() {
    override fun getLocalEnvironment(memberId: String, httpPort: Int): Map<String, String> {
        return mapOf(
            "localOverride" to "whatever"
        ).plus(
            super.getLocalEnvironment(memberId, httpPort)
        )
    }
}
