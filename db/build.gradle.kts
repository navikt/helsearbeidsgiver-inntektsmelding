val exposedVersion: String by project
val flywayVersion: String by project
val hikariVersion: String by project
val ktorVersion: String by project
val postgresqlVersion: String by project
val testcontainersPostgresqlVersion: String by project

dependencies {
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    runtimeOnly("org.postgresql:postgresql:$postgresqlVersion")

    testImplementation("org.testcontainers:postgresql:$testcontainersPostgresqlVersion")
}
