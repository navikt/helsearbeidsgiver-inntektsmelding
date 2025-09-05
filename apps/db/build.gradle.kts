dependencies {
    implementation(project(":utils-db-exposed"))
    implementation(project(":kontrakt-domene-forespoersel"))
    implementation(project(":kontrakt-domene-inntektsmelding"))

    testImplementation(testFixtures(project(":utils-db-exposed")))
    testImplementation(testFixtures(project(":kontrakt-domene-forespoersel")))
    testImplementation(testFixtures(project(":kontrakt-domene-inntektsmelding")))
}
