val hagDomeneInntektsmeldingVersion: String by project
val kotestVersion: String by project
val utilsVersion: String by project

plugins {
    id("java-test-fixtures")
}

dependencies {
    testFixturesApi("no.nav.helsearbeidsgiver:domene-inntektsmelding:$hagDomeneInntektsmeldingVersion")

    testFixturesImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
    testFixturesImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
}
