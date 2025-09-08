val hagDomeneInntektsmeldingVersion: String by project
val kotestVersion: String by project
val micrometerVersion: String by project
val mockkVersion: String by project
val rapidsAndRiversTestVersion: String by project
val rapidsAndRiversVersion: String by project
val slf4jVersion: String by project
val utilsVersion: String by project

plugins {
    id("java-test-fixtures")
}

dependencies {
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    api("org.slf4j:slf4j-api:$slf4jVersion")

    implementation(project(":utils-valkey"))
    implementation("io.micrometer:micrometer-core:$micrometerVersion")

    testImplementation(testFixtures(project(":utils-valkey")))

    testFixturesApi("com.github.navikt.tbd-libs:rapids-and-rivers-test:$rapidsAndRiversTestVersion")
    testFixturesApi("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    testFixturesApi("no.nav.helsearbeidsgiver:domene-inntektsmelding:$hagDomeneInntektsmeldingVersion")

    testFixturesImplementation(project(":utils-felles"))
    testFixturesImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
    testFixturesImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testFixturesImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testFixturesImplementation("io.mockk:mockk:$mockkVersion")
}
