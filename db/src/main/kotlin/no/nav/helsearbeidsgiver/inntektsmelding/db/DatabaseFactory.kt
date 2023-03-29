package no.nav.helsearbeidsgiver.inntektsmelding.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

class DatabaseFactory(
    dbConfig: HikariConfig
) {
    val dataSource = HikariDataSource(dbConfig)
    val db = Database.connect(dataSource)

    fun migrate() {
        Flyway.configure()
            .dataSource(dataSource)
            .lockRetryCount(10)
            .load()
            .migrate()
    }
}
