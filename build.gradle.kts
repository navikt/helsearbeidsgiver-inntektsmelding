import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("org.jmailen.kotlinter")
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.fasterxml.jackson.core:jackson-databind:2.13.4")
    }
}

allprojects {
    tasks {
        val jvmTargetVersion: String by project
        val gradleWrapperVersion: String by project

        withType<KotlinCompile> {
            kotlinOptions.jvmTarget = jvmTargetVersion
        }

        withType<Wrapper> {
            gradleVersion = gradleWrapperVersion
        }

        withType<Test> {
            useJUnitPlatform()
            testLogging {
                events("skipped", "failed")
            }
        }
    }

    repositories {
        val githubPassword: String by project

        maven("https://packages.confluent.io/maven/")
        maven("https://oss.sonatype.org")
        maven("https://jitpack.io")
        mavenCentral()
        maven {
            setUrl("https://maven.pkg.github.com/navikt/*")
            credentials {
                username = "x-access-token"
                password = githubPassword
            }
        }
    }
}

subprojects {
    group = "no.nav.helsearbeidsgiver.inntektsmelding"
    version = properties["version"] ?: "local-build"

    applyPlugins(
        "org.jetbrains.kotlin.jvm",
        "org.jmailen.kotlinter"
    )

    tasks {
        if (!project.erFellesmodul()) {
            named<Jar>("jar") {
                archiveBaseName.set("app")

                val mainClass = project.mainClass()

                doLast {
                    validateMainClassFound(mainClass)
                }

                manifest {
                    attributes["Main-Class"] = mainClass
                    attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") { it.name }
                }

                doLast {
                    configurations.runtimeClasspath.get().forEach { file ->
                        File("$buildDir/libs/${file.name}")
                            .takeUnless(File::exists)
                            ?.let(file::copyTo)
                    }
                }
            }
        }
    }

    val junitJupiterVersion: String by project
    val ktorVersion: String by project
    val mockkVersion: String by project
    val rapidsAndRiversVersion: String by project

    ext {
        set("ktorVersion", ktorVersion)
        set("rapidsAndRiversVersion", rapidsAndRiversVersion)
    }

    dependencies {
        if (!erFellesmodul())
            implementation(project(":felles"))

        testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
        testImplementation("io.mockk:mockk:$mockkVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    }
}

tasks {
    val mapper = ObjectMapper()

    create("buildMatrix") {
        doLast {
            mapper.asString(
                "project" to getBuildableProjects()
            )
                .also(::println)
        }
    }

    create("deployMatrix") {
        doLast {
            val (
                deployableProjects,
                clusters,
                exclusions,
            ) = getDeployMatrixVariables()

            mapper.asString(
                "cluster" to clusters,
                "project" to deployableProjects,
                "exclude" to exclusions
            )
                .also(::println)
        }
    }
}

fun getBuildableProjects(): List<String> {
    val changedFiles = System.getenv("CHANGED_FILES")
        ?.split(",")
        .orEmpty()

    val hasCommonChanges = changedFiles.any {
        it.startsWith("felles/") ||
            it.startsWith("build.gradle.kts") ||
            it.contains("config/nais.yml") ||
            it == ".github/workflows/build.yml" ||
            it == "Dockerfile"
    }

    return subprojects.map { it.name }
        .let { projects ->
            if (hasCommonChanges) projects.toList()
            else projects.filter { changedFiles.anyContains("$it/") }
        }
}

fun getDeployMatrixVariables(): Triple<List<String>, Set<String>, List<Map<String, String>>> {
    // map of cluster to list of apps
    val deployableProjects = getBuildableProjects().filter { File("config", it).isDirectory }

    val environments = deployableProjects.associateWith { project ->
        File("config", project)
            .listFiles()
            ?.filter { it.isFile && it.name.endsWith(".yml") }
            ?.map { it.name.removeSuffix(".yml") }
            ?.toSet()
            .orEmpty()
    }

    val clusters = environments.values.flatten().toSet()

    val exclusions = environments
        .mapValues { (_, configs) ->
            clusters.subtract(configs)
        }
        .filterValues { it.isNotEmpty() }
        .flatMap { (project, excludedClusters) ->
            excludedClusters.map {
                mapOf(
                    "project" to project,
                    "cluster" to it
                )
            }
        }

    return Triple(
        deployableProjects,
        clusters,
        exclusions
    )
}

fun PluginAware.applyPlugins(vararg ids: String) {
    ids.forEach {
        apply(plugin = it)
    }
}

fun Task.validateMainClassFound(mainClass: String) {
    val mainClassOsSpecific = mainClass.replace(".", File.separator)

    val mainClassFound = this.project.sourceSets
        .findByName("main")
        ?.output
        ?.classesDirs
        ?.asFileTree
        ?.map { it.path }
        ?.anyContains(mainClassOsSpecific)
        ?: false

    if (!mainClassFound) throw RuntimeException("Kunne ikke finne main class: $mainClass")
}

fun Project.mainClass() =
    "$group.${name.replace("-", "")}.AppKt"

fun Project.erFellesmodul() =
    name == "felles"

fun ObjectMapper.asString(vararg keyValuePairs: Pair<String, Any>): String =
    writeValueAsString(
        mapOf(*keyValuePairs)
    )

fun List<String>.anyContains(other: String): Boolean =
    this.any { it.contains(other) }
