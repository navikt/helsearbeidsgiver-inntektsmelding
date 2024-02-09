val arbeidsgiverNotifikasjonKlientVersion: String by project

dependencies {
    implementation(project(":felles-db"))

    implementation("no.nav.helsearbeidsgiver:arbeidsgiver-notifikasjon-klient:$arbeidsgiverNotifikasjonKlientVersion")

    testImplementation(testFixtures(project(":felles-db")))
}
