val aaregClientVersion: String by project
val altinnClientVersion: String by project
val arbeidsgiverNotifikasjonVersion: String by project
val brregKlientVersion: String by project
val dokarkivKlientVersion: String by project
val exposedVersion: String by project
val flywayVersion: String by project
val hikariVersion: String by project
val inntektKlientVersion: String by project
val junitJupiterVersion: String by project
val ktorVersion: String by project
val pdlKlientVersion: String by project
val postgresqlVersion: String by project
val testcontainersRedisJunitVersion: String by project
val testcontainersVersion: String by project

dependencies {
    implementation(project(":aareg"))
    implementation(project(":akkumulator"))
    implementation(project(":altinn"))
    implementation(project(":api"))
    implementation(project(":brreg"))
    implementation(project(":db"))
    implementation(project(":distribusjon"))
    implementation(project(":dokument"))
    implementation(project(":felles"))
    implementation(project(":forespoersel-mottatt"))
    implementation(project(":helsebro"))
    implementation(project(":innsending"))
    implementation(project(":inntekt"))
    implementation(project(":joark"))
    implementation(project(":notifikasjon"))
    implementation(project(":pdl"))
    implementation(project(":preutfylt"))

    implementation("no.nav.helsearbeidsgiver:aareg-client:$aaregClientVersion")
    implementation("no.nav.helsearbeidsgiver:altinn-client:$altinnClientVersion")
    implementation("no.nav.helsearbeidsgiver:arbeidsgiver-notifikasjon-klient:$arbeidsgiverNotifikasjonVersion")
    implementation("no.nav.helsearbeidsgiver:brreg-client:$brregKlientVersion")
    implementation("no.nav.helsearbeidsgiver:dokarkiv-client:$dokarkivKlientVersion")
    implementation("no.nav.helsearbeidsgiver:inntekt-klient:$inntektKlientVersion")
    implementation("no.nav.helsearbeidsgiver:pdl-client:$pdlKlientVersion")

    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")

    runtimeOnly("org.postgresql:postgresql:$postgresqlVersion")

    testApi(project(":api"))

    testImplementation(project(":felles-test"))
    testImplementation("com.redis.testcontainers:testcontainers-redis-junit:$testcontainersRedisJunitVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("org.testcontainers:kafka:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
}
