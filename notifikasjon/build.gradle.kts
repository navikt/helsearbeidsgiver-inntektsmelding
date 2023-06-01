val ktorVersion: String by project
val arbeidsgiverNotifikasjonKlientVersion: String by project

dependencies {
    // Nødvending for no.nav.helsearbeidsgiver.tokenprovider.DefaultOAuth2HttpClient, burde ikke være det
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("no.nav.helsearbeidsgiver:arbeidsgiver-notifikasjon-klient:$arbeidsgiverNotifikasjonKlientVersion")
}
