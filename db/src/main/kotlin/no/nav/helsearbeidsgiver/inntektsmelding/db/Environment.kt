package no.nav.helsearbeidsgiver.inntektsmelding.db

import com.zaxxer.hikari.HikariConfig
import no.nav.helsearbeidsgiver.felles.fromEnv
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private const val prefix = "NAIS_DATABASE_IM_DB_INNTEKTSMELDING"

data class DatabaseConfig(
    val host: String = "${prefix}_HOST".fromEnv(),
    val port: String = "${prefix}_PORT".fromEnv(),
    val name: String = "${prefix}_DATABASE".fromEnv(),
    val username2: String = "${prefix}_USERNAME".fromEnv(),
    val password2: String = "${prefix}_PASSWORD".fromEnv(),
    val url: String = "jdbc:postgresql://%s:%s/%s".format(host, port, name)
) {
    fun dbConfig(): HikariConfig =
        HikariConfig().apply {
            jdbcUrl = url
            username = username2
            password = password2
            maximumPoolSize = 1
            connectionTimeout = 30.seconds.toMillis()
            initializationFailTimeout = 1.minutes.toMillis()
            idleTimeout = 1.minutes.toMillis()
            maxLifetime = idleTimeout * 5
        }

    fun migrationConfig(): HikariConfig =
        HikariConfig().apply {
            jdbcUrl = url
            username = username2
            password = password2
            maximumPoolSize = 2
            connectionTimeout = 1.minutes.toMillis()
            initializationFailTimeout = 1.minutes.toMillis()
        }

    fun Duration.toMillis(): Long =
        toJavaDuration().toMillis()
}
