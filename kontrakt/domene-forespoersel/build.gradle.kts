plugins {
    id("java-test-fixtures")
}

dependencies {
    val hagDomeneInntektsmeldingVersion: String by project
    val utilsVersion: String by project

    testFixturesImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
    testFixturesImplementation("no.nav.helsearbeidsgiver:domene-inntektsmelding:$hagDomeneInntektsmeldingVersion")
}
