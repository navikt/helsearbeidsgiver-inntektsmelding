dependencies {
    implementation("no.nav.helsearbeidsgiver:domene-inntektsmelding")
    implementation(project(":kontrakt-domene-inntektsmelding"))
    implementation(project(":utils-db-exposed"))

    testImplementation(testFixtures("no.nav.helsearbeidsgiver:domene-inntektsmelding"))
    testImplementation(testFixtures(project(":kontrakt-domene-inntektsmelding")))
    testImplementation(testFixtures(project(":utils-db-exposed")))
}
