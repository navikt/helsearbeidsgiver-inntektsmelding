val ktorVersion: String by project

dependencies {
    val pdfboxVersion: String by project
    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")
    implementation("no.nav.helsearbeidsgiver:dokarkiv-client:0.1.4")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-json:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
}
