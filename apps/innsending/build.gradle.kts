dependencies {
    implementation("no.nav.helsearbeidsgiver:domene-inntektsmelding")
    implementation(project(":kontrakt-domene-inntektsmelding"))
    implementation(project(":kontrakt-resultat-kvittering"))
    implementation(project(":utils-kafka"))
    implementation(project(":utils-valkey"))

    testImplementation(testFixtures("no.nav.helsearbeidsgiver:domene-inntektsmelding"))
    testImplementation(testFixtures(project(":kontrakt-domene-inntektsmelding")))
    testImplementation(testFixtures(project(":utils-valkey")))
}
