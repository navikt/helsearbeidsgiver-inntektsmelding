val ktorVersion: String by project
val inntektKlientVersion: String by project

dependencies {
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("no.nav.helsearbeidsgiver:inntekt-klient:$inntektKlientVersion")
}
