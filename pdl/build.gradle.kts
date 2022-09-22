val ktorVersion: String by project

dependencies {
    implementation("no.nav.helsearbeidsgiver:pdl-client:0.2.1")
    implementation("no.nav.helsearbeidsgiver:tokenprovider:0.2.4")
    implementation("no.nav.security:token-client-core:2.1.4")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-json:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
}
