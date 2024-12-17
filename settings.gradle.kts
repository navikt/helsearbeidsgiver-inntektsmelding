rootProject.name = "helsearbeidsgiver-inntektsmelding"

val apps = File(rootDir, "apps")
    .listFiles()
    ?.map { it.name }
    .orEmpty()

include(apps)

apps.forEach {
    project(":$it").projectDir = file("apps/$it")
}

pluginManagement {
    plugins {
        val kotlinVersion: String by settings
        val kotlinterVersion: String by settings

        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("org.jmailen.kotlinter") version kotlinterVersion
    }
}
