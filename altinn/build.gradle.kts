dependencies {
    val altinnClientVersion: String by project
    val maskinportenClientVersion: String by project
    implementation("no.nav.helsearbeidsgiver:altinn-client:$altinnClientVersion")
    implementation("no.nav.helsearbeidsgiver:maskinporten-client:$maskinportenClientVersion")
}
