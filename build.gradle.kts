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
        if (!erFellesmodul()) {
            implementation(project(":felles"))
        }

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
            mapper.taskOutput(
                "project" to getBuildableProjects()
            )
        }
    }

    create("deployMatrix") {
        doLast {
            val (
                deployableProjects,
                clusters,
                exclusions
            ) = getDeployMatrixVariables()

            mapper.taskOutput(
                "project" to deployableProjects,
                "cluster" to clusters,
                "exclude" to exclusions.map { (project, cluster) ->
                    mapOf(
                        "project" to project,
                        "cluster" to cluster
                    )
                }
            )
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
            if (changedFiles.isEmpty() || hasCommonChanges) projects
            else projects.filter { changedFiles.anyContains("$it/") }
        }
}

fun getDeployMatrixVariables(): Triple<List<String>, Set<String>, List<Pair<String, String>>> {
    // map of cluster to list of apps
    val deployableProjects = getBuildableProjects().filter { File("config", it).isDirectory }

    val clustersByProject = deployableProjects.associateWith { project ->
        File("config", project)
            .list { file, name -> file.isFile && name.endsWith(".yml") }
            ?.map { it.removeSuffix(".yml") }
            ?.toSet()
            .orEmpty()
    }

    val allClusters = clustersByProject.values.flatten().toSet()

    val exclusions = clustersByProject.flatMap { (project, clusters) ->
        allClusters.subtract(clusters)
            .map { Pair(project, it) }
    }

    return Triple(
        deployableProjects,
        allClusters,
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

fun ObjectMapper.taskOutput(vararg keyValuePairs: Pair<String, Any>) {
    mapOf(*keyValuePairs)
        .let(this::writeValueAsString)
        .let(::println)
}

fun List<String>.anyContains(other: String): Boolean =
    this.any { it.contains(other) }
