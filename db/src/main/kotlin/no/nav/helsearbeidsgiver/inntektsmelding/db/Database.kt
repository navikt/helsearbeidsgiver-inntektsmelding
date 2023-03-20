package no.nav.helsearbeidsgiver.inntektsmelding.db

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database as ExposedDatabase


class Database(
    val environment: DatabaseConfig
) {
    val dataSource by lazy { HikariDataSource(environment.dbConfig()) }
    val db by lazy { ExposedDatabase.connect(dataSource) }

    fun migrate() {
        environment.migrationConfig()
            .let(::HikariDataSource)
            .also {
                Flyway.configure()
                    .dataSource(it)
                    .lockRetryCount(-1)
                    .load()
                    .migrate()
            }
            .close()
    }
}
