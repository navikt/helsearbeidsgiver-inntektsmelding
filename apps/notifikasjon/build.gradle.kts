val arbeidsgiverNotifikasjonKlientVersion: String by project

dependencies {
    implementation(project(":felles-auth"))
    implementation("no.nav.helsearbeidsgiver:arbeidsgiver-notifikasjon-klient:$arbeidsgiverNotifikasjonKlientVersion")
}
