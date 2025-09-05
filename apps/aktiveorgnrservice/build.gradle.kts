dependencies {
    implementation(project(":kontrakt-domene-arbeidsgiver"))
    implementation(project(":utils-valkey"))

    testImplementation(testFixtures(project(":utils-valkey")))
}
