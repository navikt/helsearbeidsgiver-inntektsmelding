val altinnVersion: String by project
val ktorVersion: String by project
val lettuceVersion: String by project
val mockOauth2ServerVersion: String by project
val tokenSupportVersion: String by project
val valiktorVersion: String by project

tasks {
    test {
        environment("LOGINSERVICE_IDPORTEN_DISCOVERY_URL", "http://localhost:6666/loginservice-issuer/.well-known/openid-configuration")
        environment("LOGINSERVICE_IDPORTEN_AUDIENCE", "aud-localhost")
        environment("REDIS_URL", "test_url")
    }
}

dependencies {
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.lettuce:lettuce-core:$lettuceVersion")
    implementation("no.nav.helsearbeidsgiver:altinn-client:$altinnVersion")
    implementation("no.nav.security:token-client-core:$tokenSupportVersion")
    implementation("no.nav.security:token-validation-ktor-v2:$tokenSupportVersion")
    implementation("org.valiktor:valiktor-core:$valiktorVersion")

    testImplementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("no.nav.security:mock-oauth2-server:$mockOauth2ServerVersion")
    testImplementation(kotlin("test"))

}
