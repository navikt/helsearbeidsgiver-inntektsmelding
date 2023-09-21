val altinnVersion: String by project
val ktorVersion: String by project
val lettuceVersion: String by project
val mockOauth2ServerVersion: String by project
val tokenSupportVersion: String by project
val valiktorVersion: String by project

plugins {
    id("org.hidetake.swagger.generator") version "2.19.2"
}

tasks {
    test {
        environment("IDPORTEN_WELL_KNOWN_URL", "http://localhost:6666/idporten-issuer/.well-known/openid-configuration")
        environment("IDPORTEN_AUDIENCE", "aud-localhost")
        environment("REDIS_URL", "redis://test_url:6379/0")
    }
}

swaggerSources {
    register("simba") {
        setInputFile(file("src/main/resources/openapi/documentation.yaml"))
    }
}

dependencies {
    swaggerUI("org.webjars:swagger-ui:5.6.1")
    implementation("io.swagger.codegen.v3:swagger-codegen-generators:1.0.42")

    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-openapi:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.lettuce:lettuce-core:$lettuceVersion")
    implementation("no.nav.helsearbeidsgiver:altinn-client:$altinnVersion")
    implementation("no.nav.security:token-client-core:$tokenSupportVersion")
    implementation("no.nav.security:token-validation-ktor-v2:$tokenSupportVersion")
    implementation("org.valiktor:valiktor-core:$valiktorVersion")

    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-client-core:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("no.nav.security:mock-oauth2-server:$mockOauth2ServerVersion")
}
