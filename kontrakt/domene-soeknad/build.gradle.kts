plugins {
    id("java-test-fixtures")
}

dependencies {
    val utilsVersion = project.property("utilsVersion") as String

    testFixturesImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
}
