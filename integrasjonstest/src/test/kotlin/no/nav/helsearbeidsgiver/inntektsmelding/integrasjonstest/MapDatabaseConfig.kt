package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import no.nav.helsearbeidsgiver.inntektsmelding.db.DatabaseConfig
import org.testcontainers.containers.PostgreSQLContainer

fun mapDatabaseConfig(postgreSQLContainer: PostgreSQLContainer<Nothing>): DatabaseConfig {
    return DatabaseConfig(
        postgreSQLContainer.host,
        postgreSQLContainer.firstMappedPort.toString(),
        postgreSQLContainer.databaseName,
        postgreSQLContainer.username,
        postgreSQLContainer.password
    )
}
