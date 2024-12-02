package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import com.zaxxer.hikari.HikariConfig
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

fun PostgreSQLContainer<Nothing>.toHikariConfig(): HikariConfig =
    HikariConfig().apply {
        jdbcUrl = this@toHikariConfig.jdbcUrl
        username = this@toHikariConfig.username
        password = this@toHikariConfig.password
        maximumPoolSize = 1
        connectionTimeout = 30.seconds.toMillis()
        initializationFailTimeout = 1.minutes.toMillis()
        idleTimeout = 1.minutes.toMillis()
        maxLifetime = idleTimeout * 5
    }

private fun Duration.toMillis(): Long = toJavaDuration().toMillis()
