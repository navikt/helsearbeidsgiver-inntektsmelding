plugins {
    id("java-test-fixtures")
}

dependencies {
    api(project(":kontrakt-domene-forespoersel"))
    api(project(":kontrakt-domene-soeknad"))

    testFixturesImplementation(testFixtures(project(":kontrakt-domene-forespoersel")))
}
