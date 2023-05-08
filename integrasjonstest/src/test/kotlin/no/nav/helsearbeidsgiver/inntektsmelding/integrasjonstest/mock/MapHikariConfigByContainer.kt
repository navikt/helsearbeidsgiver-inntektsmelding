package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock

import com.zaxxer.hikari.HikariConfig
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

fun mapHikariConfigByContainer(postgreSQLContainer: PostgreSQLContainer<Nothing>): HikariConfig {
    return HikariConfig().apply {
        jdbcUrl = postgreSQLContainer.jdbcUrl
        username = postgreSQLContainer.username
        password = postgreSQLContainer.password
        maximumPoolSize = 1
        connectionTimeout = 30.seconds.toMillis()
        initializationFailTimeout = 1.minutes.toMillis()
        idleTimeout = 1.minutes.toMillis()
        maxLifetime = idleTimeout * 5
    }
}

fun Duration.toMillis(): Long =
    toJavaDuration().toMillis()
