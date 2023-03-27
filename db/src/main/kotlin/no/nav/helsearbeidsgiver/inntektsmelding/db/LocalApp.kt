package no.nav.helsearbeidsgiver.inntektsmelding.db

fun main() {
    buildApp(mapHikariConfig(DatabaseConfig("127.0.0.1", "5432", "im_db", "postgres", "test")))
}
