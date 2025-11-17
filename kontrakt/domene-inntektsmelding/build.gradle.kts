plugins {
    id("java-test-fixtures")
}

dependencies {
    val utilsVersion: String by project

    // Trengs kun nå lenge vi bruker inlinet versjon av SkjemaInntektsmelding
    implementation(project(":utils-felles"))

    testFixturesImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
}
