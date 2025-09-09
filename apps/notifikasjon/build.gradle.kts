dependencies {
    val arbeidsgiverNotifikasjonKlientVersion: String by project

    implementation(project(":kontrakt-domene-forespoersel"))
    implementation(project(":utils-auth"))
    implementation("no.nav.helsearbeidsgiver:arbeidsgiver-notifikasjon-klient:$arbeidsgiverNotifikasjonKlientVersion")

    testImplementation(testFixtures(project(":kontrakt-domene-forespoersel")))
}
