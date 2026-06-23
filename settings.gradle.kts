rootProject.name = "helsearbeidsgiver-inntektsmelding"

val appsDirName = "apps"
val kontraktDirName = "kontrakt"
val utilsDirName = "utils"

val projects =
    setOf(appsDirName, kontraktDirName, utilsDirName)
        .mapNotNull { dirName ->
            File(rootDir, dirName)
                .listFiles()
                ?.map { it.name to dirName }
        }.flatten()
        .toMap()

include(projects.map { it.projectName() })

projects.forEach {
    project(":${it.projectName()}").projectDir = file("${it.value}/${it.key}")
}

pluginManagement {
    plugins {
        val kotestVersion = providers.gradleProperty("kotestVersion").get()
        val kotlinVersion = providers.gradleProperty("kotlinVersion").get()
        val kotlinterVersion = providers.gradleProperty("kotlinterVersion").get()

        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("io.kotest") version kotestVersion
        id("org.jmailen.kotlinter") version kotlinterVersion
    }
}

private fun Map.Entry<String, String>.projectName(): String =
    if (value == appsDirName) {
        key
    } else {
        "$value-$key"
    }
