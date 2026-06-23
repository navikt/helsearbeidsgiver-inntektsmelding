val lettuceVersion = project.property("lettuceVersion") as String
val mockkVersion = project.property("mockkVersion") as String
val utilsVersion = project.property("utilsVersion") as String

plugins {
    id("java-test-fixtures")
}

dependencies {
    api(project(":utils-felles"))

    implementation("io.lettuce:lettuce-core:$lettuceVersion")

    testFixturesImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
    testFixturesImplementation("io.lettuce:lettuce-core:$lettuceVersion")
    testFixturesImplementation("io.mockk:mockk:$mockkVersion")
}
