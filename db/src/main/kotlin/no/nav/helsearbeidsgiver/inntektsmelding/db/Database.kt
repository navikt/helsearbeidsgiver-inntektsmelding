package no.nav.helsearbeidsgiver.inntektsmelding.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
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
                    .lockRetryCount(-1)
                    .load()
                    .migrate()
            }
    }
}

private fun migrationConfig(conf: HikariConfig): HikariConfig =
    HikariConfig().apply {
        jdbcUrl = conf.jdbcUrl
        username = conf.username
        password = conf.password
        maximumPoolSize = 3
        connectionTimeout = 1.minutes.toMillis()
        initializationFailTimeout = 1.minutes.toMillis()
    }

private fun Duration.toMillis(): Long =
    toJavaDuration().toMillis()
