val exposedVersion: String by project
val flywayVersion: String by project
val hikariVersion: String by project
val kotestVersion: String by project
val postgresqlVersion: String by project
val testcontainersPostgresqlVersion: String by project

plugins {
    id("java-test-fixtures")
}

dependencies {
    api("com.zaxxer:HikariCP:$hikariVersion")
    api("org.jetbrains.exposed:exposed-core:$exposedVersion")
    api("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    api("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    api("org.jetbrains.exposed:exposed-json:$exposedVersion")

    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    runtimeOnly("org.postgresql:postgresql:$postgresqlVersion")

    testFixturesApi("io.kotest:kotest-assertions-core:$kotestVersion")
    testFixturesApi("io.kotest:kotest-runner-junit5:$kotestVersion")

    testFixturesImplementation("org.testcontainers:postgresql:$testcontainersPostgresqlVersion")
}
