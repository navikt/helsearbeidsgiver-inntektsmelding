val ktorVersion: String by project

dependencies {
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-json:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("no.nav.helsearbeidsgiver:arbeidsgiver-notifikasjon-klient:1.0.0")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
}
