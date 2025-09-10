dependencies {
    implementation(project(":utils-kafka"))
    implementation(project(":utils-valkey"))

    testImplementation(testFixtures(project(":utils-valkey")))
}
