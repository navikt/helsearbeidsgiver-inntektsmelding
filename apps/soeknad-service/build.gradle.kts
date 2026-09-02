dependencies {
    implementation(project(":kontrakt-domene-forespoersel"))
    implementation(project(":kontrakt-domene-soeknad"))
    implementation(project(":kontrakt-resultat-soeknad"))
    implementation(project(":utils-rapids-and-rivers"))
    implementation(project(":utils-valkey"))

    testImplementation(testFixtures(project(":kontrakt-domene-forespoersel")))
    testImplementation(testFixtures(project(":kontrakt-domene-soeknad")))
    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
}
