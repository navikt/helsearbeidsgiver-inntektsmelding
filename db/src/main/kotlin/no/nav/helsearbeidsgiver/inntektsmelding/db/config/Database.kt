package no.nav.helsearbeidsgiver.inntektsmelding.db.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helsearbeidsgiver.felles.fromEnv
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database as ExposedDatabase

class Database(
    private val config: HikariConfig
) {
    constructor(secrets: Secrets) : this(
        dbConfig(secrets)
    )

    val dataSource by lazy { HikariDataSource(config) }
    val db by lazy { ExposedDatabase.connect(dataSource) }

    fun migrate(location: String? = null) {
        migrationConfig(config)
            .let(::HikariDataSource)
            .also { dataSource ->
                Flyway.configure()
                    .dataSource(dataSource)
                    .lockRetryCount(50)
                    .let {
                        if (location != null) {
                            it.locations("filesystem:$location")
                        } else {
                            it
                        }
                    }
                    .load()
                    .migrate()
            }
            .close()
    }

    class Secrets(prefix: String) {
        val username = "${prefix}_USERNAME".fromEnv()
        val password = "${prefix}_PASSWORD".fromEnv()

        val url = "jdbc:postgresql://%s:%s/%s".format(
            "${prefix}_HOST".fromEnv(),
            "${prefix}_PORT".fromEnv(),
            "${prefix}_DATABASE".fromEnv()
        )
    }
}

private fun dbConfig(secrets: Database.Secrets): HikariConfig =
    HikariConfig().apply {
        jdbcUrl = secrets.url
        username = secrets.username
        password = secrets.password
        maximumPoolSize = 5
    }

private fun migrationConfig(config: HikariConfig): HikariConfig =
    HikariConfig().apply {
        jdbcUrl = config.jdbcUrl
        username = config.username
        password = config.password
        maximumPoolSize = 3
    }
