val ktorVersion: String by project
val kotlinVersion: String by project

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("org.valiktor:valiktor-core:0.12.0")
    implementation("no.nav.helsearbeidsgiver:altinn-client:0.1.11")
    implementation("no.nav.security:token-client-core:2.1.3")
    implementation("no.nav.security:token-validation-ktor-v2:2.1.3")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation(kotlin("test"))
}
