plugins {
    id("java-test-fixtures")
}

dependencies {
    val hagDomeneInntektsmeldingVersion = project.property("hagDomeneInntektsmeldingVersion") as String
    val utilsVersion = project.property("utilsVersion") as String

    testFixturesImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
    testFixturesImplementation("no.nav.helsearbeidsgiver:domene-inntektsmelding:$hagDomeneInntektsmeldingVersion")
}
