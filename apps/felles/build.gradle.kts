val hagDomeneInntektsmeldingVersion: String by project
val kafkaClientVersion: String by project
val kotestVersion: String by project
val lettuceVersion: String by project
val mockkVersion: String by project
val utilsVersion: String by project

plugins {
    id("java-test-fixtures")
}

dependencies {
    api("org.apache.kafka:kafka-clients:$kafkaClientVersion")

    implementation("io.lettuce:lettuce-core:$lettuceVersion")

    testFixturesApi("io.lettuce:lettuce-core:$lettuceVersion")
    testFixturesApi("no.nav.helsearbeidsgiver:domene-inntektsmelding:$hagDomeneInntektsmeldingVersion")

    testFixturesImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
    testFixturesImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testFixturesImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testFixturesImplementation("io.mockk:mockk:$mockkVersion")
}
