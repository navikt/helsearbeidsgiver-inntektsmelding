dependencies {
    implementation(project(":kontrakt-domene-forespoersel"))
    implementation(project(":utils-rapids-and-rivers"))

    testImplementation(testFixtures(project(":kontrakt-domene-forespoersel")))
    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
}
