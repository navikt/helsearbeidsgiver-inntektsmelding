dependencies {
    implementation(project(":kontrakt-domene-forespoersel"))
    implementation(project(":kontrakt-domene-inntektsmelding"))
    implementation(project(":utils-db-exposed"))
    implementation(project(":utils-rapids-and-rivers"))

    testImplementation(testFixtures(project(":kontrakt-domene-forespoersel")))
    testImplementation(testFixtures(project(":kontrakt-domene-inntektsmelding")))
    testImplementation(testFixtures(project(":utils-db-exposed")))
    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
}
