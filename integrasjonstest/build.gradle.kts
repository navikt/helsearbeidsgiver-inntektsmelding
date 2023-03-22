val exposedVersion: String by project
val flywayVersion: String by project
val hikariVersion: String by project
val ktorVersion: String by project
val postgresqlVersion: String by project
val testcontainersPostgresqlVersion: String by project
val aaregClientVersion: String by project

dependencies {
    implementation(project(":dokument"))
    implementation(project(":felles"))
    implementation(project(":aareg"))
    implementation(project(":akkumulator"))
    implementation(project(":brreg"))
    implementation(project(":db"))
    implementation(project(":distribusjon"))
    implementation(project(":forespoersel-mottatt"))
    implementation(project(":helsebro"))
    implementation(project(":inntekt"))
    implementation(project(":joark"))
    implementation(project(":notifikasjon"))
    implementation(project(":pdl"))
    implementation(project(":preutfylt"))
    implementation(project(":innsending"))
    implementation(project(":api"))
    testApi(project(":api"))
    testImplementation("com.redis.testcontainers:testcontainers-redis-junit:1.6.2")
    testImplementation("org.testcontainers:postgresql:1.17.6")
    testImplementation("org.testcontainers:kafka:1.17.6")

    implementation("org.junit.jupiter:junit-jupiter:5.8.1")

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
    implementation("no.nav.helsearbeidsgiver:brreg-client:0.3.0")
    implementation("no.nav.helsearbeidsgiver:pdl-client:0.2.1")
    implementation("no.nav.helsearbeidsgiver:pdl-client:0.2.1")
    implementation("no.nav.helsearbeidsgiver:arbeidsgiver-notifikasjon-klient:0.3.2")
    implementation("no.nav.helsearbeidsgiver:dokarkiv-client:0.1.9")
    implementation("no.nav.helsearbeidsgiver:inntekt-klient:0.1.9")
}
