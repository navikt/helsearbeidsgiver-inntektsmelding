package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.felles.fromEnv

object Environment {
    object Database {
        private const val prefix = "NAIS_DATABASE_IM_DB_INNTEKTSMELDING"
        val host = "${prefix}_HOST".fromEnv()
        val port = "${prefix}_PORT".fromEnv()
        val name = "${prefix}_DATABASE".fromEnv()
        val username = "${prefix}_USERNAME".fromEnv()
        val password = "${prefix}_PASSWORD".fromEnv()
    }
}
