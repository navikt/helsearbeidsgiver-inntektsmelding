dependencies {
    implementation(project(":kontrakt-domene-bro-forespoersel"))
    implementation(project(":kontrakt-domene-forespoersel"))
    implementation(project(":kontrakt-kafkatopic-pri"))
    implementation(project(":utils-kafka"))

    testImplementation(testFixtures(project(":kontrakt-domene-forespoersel")))
}
