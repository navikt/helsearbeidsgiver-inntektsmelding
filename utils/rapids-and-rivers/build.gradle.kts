val hagDomeneInntektsmeldingVersion = project.property("hagDomeneInntektsmeldingVersion") as String
val kotestVersion = project.property("kotestVersion") as String
val micrometerVersion = project.property("micrometerVersion") as String
val mockkVersion = project.property("mockkVersion") as String
val rapidsAndRiversVersion = project.property("rapidsAndRiversVersion") as String
val utilsVersion = project.property("utilsVersion") as String

plugins {
    id("java-test-fixtures")
}

dependencies {
    api(project(":kontrakt-kafkatopic-pri"))
    api(project(":utils-felles"))
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")

    implementation(project(":utils-valkey"))
    implementation("io.micrometer:micrometer-core:$micrometerVersion")

    testImplementation(testFixtures(project(":utils-felles")))
    testImplementation(testFixtures(project(":utils-valkey")))

    testFixturesApi("com.github.navikt.rapids-and-rivers:rapids-and-rivers-test:$rapidsAndRiversVersion")
    testFixturesApi("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    testFixturesApi("no.nav.helsearbeidsgiver:domene-inntektsmelding:$hagDomeneInntektsmeldingVersion")

    testFixturesImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
    testFixturesImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testFixturesImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testFixturesImplementation("io.mockk:mockk:$mockkVersion")
}
