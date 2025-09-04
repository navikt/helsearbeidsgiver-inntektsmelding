val lettuceVersion: String by project
val mockkVersion: String by project
val utilsVersion: String by project

plugins {
    id("java-test-fixtures")
}

dependencies {
    implementation("io.lettuce:lettuce-core:$lettuceVersion")

    testFixturesImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
    testFixturesImplementation("io.lettuce:lettuce-core:$lettuceVersion")
    testFixturesImplementation("io.mockk:mockk:$mockkVersion")
}
