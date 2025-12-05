dependencies {
    val arbeidsgiverNotifikasjonKlientVersion: String by project

    implementation("no.nav.helsearbeidsgiver:domene-inntektsmelding")
    implementation(project(":utils-auth"))
    implementation("no.nav.helsearbeidsgiver:arbeidsgiver-notifikasjon-klient:$arbeidsgiverNotifikasjonKlientVersion")

    testImplementation(testFixtures("no.nav.helsearbeidsgiver:domene-inntektsmelding"))
}
