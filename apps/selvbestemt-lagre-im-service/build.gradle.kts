dependencies {
    implementation(project(":utils-rapids-and-rivers"))
    implementation(project(":utils-valkey"))

    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
    testImplementation(testFixtures(project(":utils-valkey")))
}
