dependencies {
    implementation(project(":utils-kafka"))
    implementation(project(":kontrakt-domene-bro-forespoersel"))
    implementation(project(":kontrakt-domene-forespoersel"))
    implementation(project(":kontrakt-kafkatopic-pri"))

    testImplementation(testFixtures(project(":kontrakt-domene-forespoersel")))
}
