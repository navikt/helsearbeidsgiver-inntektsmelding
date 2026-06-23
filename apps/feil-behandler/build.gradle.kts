val bakgrunnsjobbVersion = project.property("bakgrunnsjobbVersion") as String
val flywayVersion = project.property("flywayVersion") as String
val hikariVersion = project.property("hikariVersion") as String
val postgresqlVersion = project.property("postgresqlVersion") as String
val testcontainersVersion = project.property("testcontainersVersion") as String

dependencies {
    implementation(project(":utils-rapids-and-rivers"))
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("no.nav.helsearbeidsgiver:hag-bakgrunnsjobb:$bakgrunnsjobbVersion")

    runtimeOnly("org.postgresql:postgresql:$postgresqlVersion")

    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainersVersion")
}
