dependencies {
    implementation(project(":utils-kafka"))
    implementation(project(":utils-rapids-and-rivers"))

    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
}
