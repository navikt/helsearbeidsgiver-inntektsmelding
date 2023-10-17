package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.felles.app.LocalApp
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.DatabaseConfig
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.mapHikariConfig

fun main() {
    val env = LocalApp().setupEnvironment("im-db", 9090)
    val rapid = buildApp(mapHikariConfig(DatabaseConfig("127.0.0.1", "5432", "im_db", "postgres", "test")), env)

    rapid.start()
}
