package no.nav.helsearbeidsgiver.felles.db.test

import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.style.FunSpec
import no.nav.helsearbeidsgiver.felles.db.Database
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer

abstract class FunSpecWithDb(
    table: List<Table>,
    body: FunSpec.(Database) -> Unit
) : FunSpec({
    val db = Database(dbConfig())
        .configureFlyway()

    beforeEach {
        transaction {
            table.forEach { it.deleteAll() }
        }
    }

    body(db)
})

private fun dbConfig(): HikariConfig {
    val postgres = postgres()
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

// TODO fiks postgres-versjon
private fun postgres(): PostgreSQLContainer<Nothing> =
    PostgreSQLContainer<Nothing>("postgres:14").apply {
        withReuse(true)
        withLabel("app-navn", "test-database")
        start()
        println(
            "🎩 Databasen er startet opp, portnummer: $firstMappedPort, jdbcUrl: jdbc:postgresql://localhost:$firstMappedPort/test, credentials: test og test"
        )
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
