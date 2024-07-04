package no.nav.helsearbeidsgiver.felles.db.exposed.test

import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.style.FunSpec
import no.nav.helsearbeidsgiver.felles.db.exposed.Database
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer
import org.jetbrains.exposed.sql.Database as ExposedDatabase

abstract class FunSpecWithDb(
    table: List<Table>,
    body: FunSpec.(ExposedDatabase) -> Unit
) : FunSpec({
    val db = Database(dbConfig())
        .configureFlyway()

    beforeEach {
        transaction {
            table.forEach { it.deleteAll() }
        }
    }

    body(db.db)
})

fun postgresContainer(): PostgreSQLContainer<Nothing> =
    PostgreSQLContainer<Nothing>("postgres:14").apply {
        setCommand("postgres", "-c", "fsync=off", "-c", "log_statement=all", "-c", "wal_level=logical")
    }

private fun PostgreSQLContainer<Nothing>.setupAndStart(): PostgreSQLContainer<Nothing> =
    apply {
        withReuse(true)
        withLabel("app-navn", "test-database")
        start()
        println(
            "🎩 Databasen er startet opp, portnummer: $firstMappedPort, jdbcUrl: jdbc:postgresql://localhost:$firstMappedPort/test, credentials: test og test"
        )
    }

private fun dbConfig(): HikariConfig {
    val postgres = postgresContainer().setupAndStart()
    return HikariConfig().apply {
        jdbcUrl = postgres.jdbcUrl
        username = postgres.username
        password = postgres.password
        maximumPoolSize = 5
        minimumIdle = 1
        idleTimeout = 500001
        connectionTimeout = 10000
        maxLifetime = 600001
        initializationFailTimeout = 5000
    }
}

private fun Database.configureFlyway(): Database =
    also {
        Flyway.configure()
            .dataSource(it.dataSource)
            .failOnMissingLocations(true)
            .cleanDisabled(false)
            .load()
            .also(Flyway::clean)
            .migrate()
    }
