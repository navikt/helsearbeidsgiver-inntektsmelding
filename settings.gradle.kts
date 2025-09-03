import kotlin.collections.map

rootProject.name = "helsearbeidsgiver-inntektsmelding"

val projects =
    setOf(
        "apps",
        "utils"
    )
        .mapNotNull { dirName ->
            File(rootDir, dirName)
                .listFiles()
                ?.map { it.name to dirName }
        }
        .flatten()
        .toMap()

include(projects.map { it.projectName() })

projects.forEach {
    project(":${it.projectName()}").projectDir = file("${it.value}/${it.key}")
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

private fun Map.Entry<String, String>.projectName(): String =
    "$value-$key"
