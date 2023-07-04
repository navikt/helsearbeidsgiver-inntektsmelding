plugins {
    id("java-test-fixtures")
}

dependencies {
    val kotlinSerializationVersion: String by project
    val utilsVersion: String by project

    testFixturesImplementation(project(":felles"))
    testFixturesImplementation(project(":felles-test"))

    testFixturesImplementation("no.nav.helsearbeidsgiver:utils:$utilsVersion")
    testFixturesImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")
}
