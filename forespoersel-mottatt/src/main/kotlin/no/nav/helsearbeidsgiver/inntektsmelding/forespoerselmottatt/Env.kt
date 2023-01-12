package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import no.nav.helsearbeidsgiver.felles.fromEnv

object Env {
    object Database {
        private const val prefix = "NAIS_DATABASE_IM_FORESPOERSEL_MOTTATT_SPLEIS_FORESPOERSEL"
        val host = "${prefix}_HOST".fromEnv()
        val port = "${prefix}_PORT".fromEnv()
        val name = "${prefix}_DATABASE".fromEnv()
        val username = "${prefix}_USERNAME".fromEnv()
        val password = "${prefix}_PASSWORD".fromEnv()
    }
}
