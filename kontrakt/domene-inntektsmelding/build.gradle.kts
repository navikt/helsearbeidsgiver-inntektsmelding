plugins {
    id("java-test-fixtures")
}

dependencies {
    val utilsVersion: String by project

    // TODO Fjern etter overgangsfase (trengs kun så lenge vi bruker inlinet versjon av SkjemaInntektsmelding)
    implementation(project(":utils-felles"))

    testFixturesImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
}
