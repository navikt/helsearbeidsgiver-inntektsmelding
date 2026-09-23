plugins {
    id("java-test-fixtures")
}

dependencies {
    val utilsVersion = project.property("utilsVersion") as String

    api(project(":kontrakt-domene-forespoersel"))
    api(project(":kontrakt-domene-soeknad"))

    testFixturesImplementation(testFixtures(project(":kontrakt-domene-forespoersel")))
    testFixturesImplementation(testFixtures(project(":kontrakt-domene-soeknad")))
    testFixturesImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
}
