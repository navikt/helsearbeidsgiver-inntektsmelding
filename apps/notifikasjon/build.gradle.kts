val arbeidsgiverNotifikasjonKlientVersion: String by project

dependencies {
    implementation(project(":utils-auth"))
    implementation("no.nav.helsearbeidsgiver:arbeidsgiver-notifikasjon-klient:$arbeidsgiverNotifikasjonKlientVersion")
}
