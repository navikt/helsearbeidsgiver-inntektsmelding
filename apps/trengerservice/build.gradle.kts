dependencies {
    implementation(project(":kontrakt-domene-forespoersel"))
    implementation(project(":kontrakt-resultat-forespoersel"))
    implementation(project(":utils-rapids-and-rivers"))
    implementation(project(":utils-valkey"))

    testImplementation(testFixtures(project(":kontrakt-domene-forespoersel")))
    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
    testImplementation(testFixtures(project(":utils-valkey")))
}
