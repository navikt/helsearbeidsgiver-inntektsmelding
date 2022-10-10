val ktorVersion: String by project

dependencies {
    implementation(project(":felles"))
    implementation("no.nav.helsearbeidsgiver:tokenprovider:0.2.6")
    implementation("no.nav.helsearbeidsgiver:inntekt-klient:0.1.2")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-json:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
}
