package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

class Database(
    dbConfig: HikariConfig
) {
    val dataSource by lazy { HikariDataSource(dbConfig) }
    private val config = dbConfig
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
        maximumPoolSize = 1
    }
