dependencies {
    implementation(project(":kontrakt-domene-forespoersel"))
    implementation(project(":kontrakt-resultat-tilgang"))
    implementation(project(":utils-rapids-and-rivers"))
    implementation(project(":utils-valkey"))

    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
}
