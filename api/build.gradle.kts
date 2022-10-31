val altinnVersion: String by project
val ktorVersion: String by project
val lettuceVersion: String by project
val valiktorVersion: String by project

tasks {
    test {
        environment("REDIS_URL", "test_url")
    }
}

tasks {
    test {
        environment("REDIS_URL", "test_url")
    }
}

dependencies {
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.lettuce:lettuce-core:$lettuceVersion")
    implementation("no.nav.helsearbeidsgiver:altinn-client:$altinnVersion")
    implementation("org.valiktor:valiktor-core:$valiktorVersion")

    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation(kotlin("test"))
}
