val exposedVersion: String by project
val flywayVersion: String by project
val hikariVersion: String by project
val ktorVersion: String by project
val postgresqlVersion: String by project
val testcontainersPostgresqlVersion: String by project
val aaregClientVersion: String by project
val brregClientVersion: String by project
val arbeidsgiverNotifikasjonKlientVersion: String by project
val pdlClientVersion: String by project
val testcontainerKafkaVersion: String by project
val testcontainerRedisVersion: String by project
val junitJupiterVersion: String by project

dependencies {
    implementation(project(":dokument"))
    implementation(project(":felles"))
    implementation(project(":akkumulator"))
    implementation(project(":db"))
    implementation(project(":api"))
    implementation(project(":aareg"))
    implementation(project(":brreg"))
    implementation(project(":notifikasjon"))
    implementation(project(":pdl"))
    implementation(project(":forespoersel-mottatt"))

    testImplementation("com.redis.testcontainers:testcontainers-redis-junit:$testcontainerRedisVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersPostgresqlVersion")
    testImplementation("org.testcontainers:kafka:$testcontainerKafkaVersion")

    implementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")

    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    runtimeOnly("org.postgresql:postgresql:$postgresqlVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")

    implementation("no.nav.helsearbeidsgiver:aareg-client:$aaregClientVersion")
    implementation("no.nav.helsearbeidsgiver:brreg-client:$brregClientVersion")
    implementation("no.nav.helsearbeidsgiver:pdl-client:$pdlClientVersion")
    implementation("no.nav.helsearbeidsgiver:arbeidsgiver-notifikasjon-klient:$arbeidsgiverNotifikasjonKlientVersion")
}
