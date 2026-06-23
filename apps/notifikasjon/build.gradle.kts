dependencies {
    val arbeidsgiverNotifikasjonKlientVersion = project.property("arbeidsgiverNotifikasjonKlientVersion") as String

    implementation(project(":kontrakt-domene-forespoersel"))
    implementation(project(":utils-auth"))
    implementation(project(":utils-rapids-and-rivers"))
    implementation("no.nav.helsearbeidsgiver:arbeidsgiver-notifikasjon-klient:$arbeidsgiverNotifikasjonKlientVersion")

    testImplementation(testFixtures(project(":kontrakt-domene-forespoersel")))
    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
}
