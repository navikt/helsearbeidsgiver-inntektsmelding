plugins {
    id("java-test-fixtures")
}

dependencies {
    val utilsVersion: String by project

    testFixturesImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
}
