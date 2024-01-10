val bakgrunnsjobbVersion: String by project
val flywayVersion: String by project
val hikariVersion: String by project
val postgresqlVersion: String by project
val testcontainersPostgresqlVersion: String by project

dependencies {
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("no.nav.helsearbeidsgiver:hag-bakgrunnsjobb:$bakgrunnsjobbVersion")

    runtimeOnly("org.postgresql:postgresql:$postgresqlVersion")

    testImplementation("org.testcontainers:postgresql:$testcontainersPostgresqlVersion")
}
