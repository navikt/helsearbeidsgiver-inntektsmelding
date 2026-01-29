package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.hag.simba.utils.felles.utils.fromEnv
import org.flywaydb.core.Flyway

class Database(
    secretsPrefix: String,
) {
    private val secrets = Secrets(secretsPrefix)

    val dataSource by lazy { HikariDataSource(dbConfig(secrets)) }

    fun migrate() {
        migrationConfig(secrets)
            .let(::HikariDataSource)
            .also {
                Flyway
                    .configure()
                    .dataSource(it)
                    .lockRetryCount(50)
                    .load()
                    .migrate()
            }.close()
    }
}

private fun dbConfig(secrets: Secrets): HikariConfig =
    HikariConfig().apply {
        jdbcUrl = secrets.url
        username = secrets.username
        password = secrets.password
        maximumPoolSize = 5
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
        driverClassName = "org.postgresql.Driver"
    }

private fun migrationConfig(secrets: Secrets): HikariConfig =
    HikariConfig().apply {
        jdbcUrl = secrets.url
        username = secrets.username
        password = secrets.password
        maximumPoolSize = 3
    }

private class Secrets(
    prefix: String,
) {
    val username = "${prefix}_USERNAME".fromEnv()
    val password = "${prefix}_PASSWORD".fromEnv()

    val url =
        "jdbc:postgresql://%s:%s/%s".format(
            "${prefix}_HOST".fromEnv(),
            "${prefix}_PORT".fromEnv(),
            "${prefix}_DATABASE".fromEnv(),
        )
}
