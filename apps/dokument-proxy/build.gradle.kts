val ktorVersion: String by project
val mockOauth2ServerVersion: String by project
val tokenSupportVersion: String by project

tasks {
    test {
        environment("LPS_API_SCOPE", "api://dev-gcp.helsearbeidsgiver.sykepenger-im-lps-api/.default")
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

    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-client-core:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("no.nav.security:mock-oauth2-server:$mockOauth2ServerVersion")
}
