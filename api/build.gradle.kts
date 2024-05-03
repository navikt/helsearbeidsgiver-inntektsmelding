val ktorVersion: String by project
val lettuceVersion: String by project
val mockOauth2ServerVersion: String by project
val tokenSupportVersion: String by project
val valiktorVersion: String by project

tasks {
    test {
        environment("IDPORTEN_WELL_KNOWN_URL", "http://localhost:6666/idporten-issuer/.well-known/openid-configuration")
        environment("IDPORTEN_AUDIENCE", "aud-localhost")
        environment("REDIS_URL", "redis://test_url:6379/0")
    }
}

dependencies {
    constraints {
        // En transitiv avhengighet i ktor 2.3.5. Kan trolig fjernes ved nyere versjoner.
        implementation("io.netty:netty-codec-http2:4.1.100.Final") {
            because("https://github.com/navikt/helsearbeidsgiver-inntektsmelding/security/dependabot/14")
        }
    }

    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.lettuce:lettuce-core:$lettuceVersion")
    implementation("no.nav.security:token-client-core:$tokenSupportVersion")
    implementation("no.nav.security:token-validation-ktor-v2:$tokenSupportVersion")
    implementation("org.valiktor:valiktor-core:$valiktorVersion")

    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-client-core:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("no.nav.security:mock-oauth2-server:$mockOauth2ServerVersion")
    testImplementation(project(":felles-test"))
}
