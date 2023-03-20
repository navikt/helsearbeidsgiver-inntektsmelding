package no.nav.helsearbeidsgiver.inntektsmelding.db

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database as ExposedDatabase


class Database(
    val databaseConfig: DatabaseConfig
) {
    val dataSource by lazy { HikariDataSource(databaseConfig.dbConfig()) }
    val db by lazy { ExposedDatabase.connect(dataSource) }

    fun migrate() {
        databaseConfig.migrationConfig()
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
