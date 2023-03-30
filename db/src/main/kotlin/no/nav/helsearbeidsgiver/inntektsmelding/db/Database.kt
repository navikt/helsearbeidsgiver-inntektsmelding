package no.nav.helsearbeidsgiver.inntektsmelding.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database as ExposedDatabase

class Database(
    dbConfig: HikariConfig
) {
    val dataSource by lazy { HikariDataSource(dbConfig) }
    val db by lazy { ExposedDatabase.connect(dataSource) }
    val config = dbConfig
    fun migrate() {
        migrationConfig(config)
            .let(::HikariDataSource)
            .also {
                Flyway.configure()
                    .dataSource(it)
                    .lockRetryCount(50)
                    .load()
                    .migrate()
            }.close()
    }
}

private fun migrationConfig(conf: HikariConfig): HikariConfig =
    HikariConfig().apply {
        jdbcUrl = conf.jdbcUrl
        username = conf.username
        password = conf.password
        maximumPoolSize = 3
    }
