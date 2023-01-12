package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val DB_URL = "jdbc:postgresql://%s:%s/%s".format(Env.Database.host, Env.Database.port, Env.Database.name)

class Db(
    dbConfig: HikariConfig = dbConfig()
) {
    val dataSource by lazy { HikariDataSource(dbConfig) }

    // TODO rename
    val db by lazy { Database.connect(dataSource) }

    fun migrate() {
        migrationConfig()
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

private fun dbConfig(): HikariConfig =
    HikariConfig().apply {
        jdbcUrl = DB_URL
        username = Env.Database.username
        password = Env.Database.password
        maximumPoolSize = 1
        connectionTimeout = 30.seconds.toMillis()
        initializationFailTimeout = 1.minutes.toMillis()
        idleTimeout = 1.minutes.toMillis()
        maxLifetime = idleTimeout * 5
    }

private fun migrationConfig(): HikariConfig =
    HikariConfig().apply {
        jdbcUrl = DB_URL
        username = Env.Database.username
        password = Env.Database.password
        maximumPoolSize = 2
        connectionTimeout = 1.minutes.toMillis()
        initializationFailTimeout = 1.minutes.toMillis()
    }

private fun Duration.toMillis(): Long =
    toJavaDuration().toMillis()
