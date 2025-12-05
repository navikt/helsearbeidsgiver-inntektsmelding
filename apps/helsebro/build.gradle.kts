dependencies {
    implementation("no.nav.helsearbeidsgiver:domene-inntektsmelding")
    implementation(project(":utils-kafka"))

    testImplementation(testFixtures("no.nav.helsearbeidsgiver:domene-inntektsmelding"))
}
