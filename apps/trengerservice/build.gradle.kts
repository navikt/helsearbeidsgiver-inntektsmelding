dependencies {
    implementation(project(":kontrakt-domene-forespoersel"))
    implementation(project(":kontrakt-resultat-forespoersel"))
    implementation(project(":utils-valkey"))

    testImplementation(testFixtures(project(":kontrakt-domene-forespoersel")))
    testImplementation(testFixtures(project(":utils-valkey")))
}
