val ktorVersion: String by project

dependencies {
    implementation("io.ktor:ktor-client-apache5:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
}

tasks {
    test {
        environment("NAIS_TOKEN_ENDPOINT", "http://localhost/mock-token")
        environment("NAIS_TOKEN_EXCHANGE_ENDPOINT", "http://localhost/mock-exchange")
        environment("NAIS_TOKEN_INTROSPECTION_ENDPOINT", "http://localhost/mock-introspection")
    }
}
