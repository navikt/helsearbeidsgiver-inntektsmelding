dependencies {
    val arbeidsgiverNotifikasjonKlientVersion: String by project

    implementation(project(":utils-auth"))
    implementation(project(":kontrakt-domene-forespoersel"))
    implementation("no.nav.helsearbeidsgiver:arbeidsgiver-notifikasjon-klient:$arbeidsgiverNotifikasjonKlientVersion")

    testImplementation(testFixtures(project(":kontrakt-domene-forespoersel")))
}
