package no.nav.helsearbeidsgiver.inntektsmelding.db

import com.zaxxer.hikari.HikariConfig
import no.nav.helsearbeidsgiver.felles.fromEnv

private const val prefix = "NAIS_DATABASE_IM_DB_INNTEKTSMELDING"

data class DatabaseConfig(
    val host: String = "${prefix}_HOST".fromEnv(),
    val port: String = "${prefix}_PORT".fromEnv(),
    val name: String = "${prefix}_DATABASE".fromEnv(),
    val username: String = "${prefix}_USERNAME".fromEnv(),
    val password: String = "${prefix}_PASSWORD".fromEnv(),
    val url: String = "jdbc:postgresql://%s:%s/%s".format(host, port, name)
)

fun mapHikariConfig(databaseConfig: DatabaseConfig): HikariConfig {
    return HikariConfig().apply {
        jdbcUrl = databaseConfig.url
        username = databaseConfig.username
        password = databaseConfig.password
    }
}
