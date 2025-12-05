dependencies {
    implementation("no.nav.helsearbeidsgiver:domene-inntektsmelding")
    implementation(project(":kontrakt-resultat-forespoersel"))
    implementation(project(":utils-valkey"))

    testImplementation(testFixtures("no.nav.helsearbeidsgiver:domene-inntektsmelding:"))
    testImplementation(testFixtures(project(":utils-valkey")))
}
