val exposedVersion = project.property("exposedVersion") as String
val flywayVersion = project.property("flywayVersion") as String
val hikariVersion = project.property("hikariVersion") as String
val kotestVersion = project.property("kotestVersion") as String
val postgresqlVersion = project.property("postgresqlVersion") as String
val testcontainersVersion = project.property("testcontainersVersion") as String

plugins {
    id("java-test-fixtures")
}

dependencies {
    api("com.zaxxer:HikariCP:$hikariVersion")
    api("org.jetbrains.exposed:exposed-core:$exposedVersion")
    api("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    api("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    api("org.jetbrains.exposed:exposed-json:$exposedVersion")

    implementation(project(":utils-felles"))
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    runtimeOnly("org.postgresql:postgresql:$postgresqlVersion")

    testFixturesApi("io.kotest:kotest-assertions-core:$kotestVersion")
    testFixturesApi("io.kotest:kotest-runner-junit5:$kotestVersion")

    testFixturesImplementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    testFixturesImplementation("org.testcontainers:testcontainers-postgresql:$testcontainersVersion")
}
