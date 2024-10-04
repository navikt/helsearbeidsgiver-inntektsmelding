val kotestVersion: String by project
val lettuceVersion: String by project
val mockkVersion: String by project
val prometheusVersion: String by project
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

    implementation("io.lettuce:lettuce-core:$lettuceVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    testFixturesApi("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    testFixturesApi("com.github.navikt.tbd-libs:rapids-and-rivers-test:$rapidsAndRiversTestVersion")
    testFixturesApi("io.lettuce:lettuce-core:$lettuceVersion")

    testFixturesImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testFixturesImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testFixturesImplementation("io.mockk:mockk:$mockkVersion")
    testFixturesImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
}
