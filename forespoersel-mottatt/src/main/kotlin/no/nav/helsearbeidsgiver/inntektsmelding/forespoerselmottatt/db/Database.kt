package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt.Env
import org.flywaydb.core.Flyway
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import org.jetbrains.exposed.sql.Database as ExposedDatabase

private val DB_URL = "jdbc:postgresql://%s:%s/%s".format(Env.Database.host, Env.Database.port, Env.Database.name)

class Database(
    dbConfig: HikariConfig = dbConfig()
) {
    val dataSource by lazy { HikariDataSource(dbConfig) }
    val db by lazy { ExposedDatabase.connect(dataSource) }

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
