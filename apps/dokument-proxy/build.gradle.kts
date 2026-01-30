val ktorVersion: String by project
val mockOauth2ServerVersion: String by project
val tokenSupportVersion: String by project

tasks {
    test {
        environment("LPS_API_TARGET", "dev-gcp.helsearbeidsgiver.sykepenger-im-lps-api")
        environment("LPS_API_BASE_URL", "http://sykepenger-im-lps-api")
    }
}

dependencies {
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation(project(":utils-auth"))

    implementation("io.ktor:ktor-client-apache5:${ktorVersion}")
    implementation("io.ktor:ktor-client-content-negotiation:${ktorVersion}")
    testImplementation("io.ktor:ktor-client-core:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("no.nav.security:mock-oauth2-server:$mockOauth2ServerVersion")
}
