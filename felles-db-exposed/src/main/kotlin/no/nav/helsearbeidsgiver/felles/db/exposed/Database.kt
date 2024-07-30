package no.nav.helsearbeidsgiver.felles.db.exposed

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helsearbeidsgiver.felles.fromEnv
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database as ExposedDatabase

class Database(
    private val config: HikariConfig,
) {
    constructor(secretsPrefix: String) : this(
        dbConfig(Secrets(secretsPrefix)),
    )

    val dataSource by lazy { HikariDataSource(config) }
    val db by lazy { ExposedDatabase.connect(dataSource) }

    fun migrate(location: String? = null) {
        migrationConfig(config)
            .let(::HikariDataSource)
            .also { dataSource ->
                Flyway
                    .configure()
                    .dataSource(dataSource)
                    .lockRetryCount(50)
                    .let {
                        if (location != null) {
                            it.locations("filesystem:$location")
                        } else {
                            it
                        }
                    }.load()
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
    }

private fun migrationConfig(config: HikariConfig): HikariConfig =
    HikariConfig().apply {
        jdbcUrl = config.jdbcUrl
        username = config.username
        password = config.password
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
