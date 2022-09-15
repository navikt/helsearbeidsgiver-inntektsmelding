import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val githubPassword: String by project
val jvmTargetVersion: String by project
val gradleWrapperVersion: String by project
val junitJupiterVersion: String by project
val rapidsAndRiversVersion: String by project
val ktorVersion: String by project
val mockkVersion: String by project

plugins {
    kotlin("jvm") version "1.7.20-RC"
    id("org.jmailen.kotlinter") version "3.10.0"
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies { "classpath"(group = "com.fasterxml.jackson.core", name = "jackson-databind", version = "2.13.2.2") }
}

val mapper = ObjectMapper()

fun getBuildableProjects(): List<Project> {
    val changedFiles = System.getenv("CHANGED_FILES")?.split(",") ?: emptyList()
    val commonChanges = changedFiles.any {
        it.startsWith("felles/") || it.contains("config/nais.yml") ||
            it.startsWith("build.gradle.kts") || it == ".github/workflows/build.yml" ||
            it == "Dockerfile"
    }
    if (changedFiles.isEmpty() || commonChanges) return subprojects.toList()
    return subprojects.filter { project -> changedFiles.any { path -> path.contains("${project.name}/") } }
}

fun getDeployableProjects() = getBuildableProjects()
    .filter { project -> File("config", project.name).isDirectory }

tasks.create("buildMatrix") {
    doLast {
        println(
            mapper.writeValueAsString(
                mapOf(
                    "project" to getBuildableProjects().map { it.name }
                )
            )
        )
    }
}
tasks.create("deployMatrix") {
    doLast {
        // map of cluster to list of apps
        val deployableProjects = getDeployableProjects().map { it.name }
        val environments = deployableProjects
            .map { project ->
                project to (
                    File("config", project)
                        .listFiles()
                        ?.filter { it.isFile && it.name.endsWith(".yml") }
                        ?.map { it.name.removeSuffix(".yml") }
                        ?: emptyList()
                    )
            }.toMap()

        val clusters = environments.flatMap { it.value }.distinct()
        val exclusions = environments
            .mapValues { (_, configs) ->
                clusters.filterNot { it in configs }
            }
            .filterValues { it.isNotEmpty() }
            .flatMap { (app, clusters) ->
                clusters.map { cluster ->
                    mapOf(
                        "project" to app,
                        "cluster" to cluster
                    )
                }
            }

        println(
            mapper.writeValueAsString(
                mapOf(
                    "cluster" to clusters,
                    "project" to deployableProjects,
                    "exclude" to exclusions
                )
            )
        )
    }
}

allprojects {
    group = "no.nav.helsearbeidsgiver.inntektsmelding"
    version = properties["version"] ?: "local-build"

    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        if (!erFellesmodul()) implementation(project(":felles"))
        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    }

    repositories {
        maven("https://packages.confluent.io/maven/")
        maven("https://oss.sonatype.org")
        maven("https://jitpack.io")
        mavenCentral()
        maven {
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: "x-access-token"
                password = System.getenv("GITHUB_TOKEN") ?: githubPassword
            }
            setUrl("https://maven.pkg.github.com/navikt/*")
        }
    }

    tasks {
        withType<KotlinCompile> {
            kotlinOptions.jvmTarget = jvmTargetVersion
        }

        named<KotlinCompile>("compileTestKotlin") {
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
}

subprojects {
    ext {
        set("ktorVersion", ktorVersion)
        set("rapidsAndRiversVersion", rapidsAndRiversVersion)
    }
    dependencies {
        testImplementation("io.mockk:mockk:$mockkVersion")
        testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
    }
    tasks {
        if (!project.erFellesmodul()) {
            named<Jar>("jar") {
                archiveBaseName.set("app")

                val mainClass = project.mainClass()

                doLast {
                    val mainClassFound = this.project.sourceSets.findByName("main")?.let {
                        it.output.classesDirs.asFileTree.any { it.path.contains(mainClass.replace(".", File.separator)) }
                    } ?: false

                    if (!mainClassFound) throw RuntimeException("Kunne ikke finne main class: $mainClass")
                }

                manifest {
                    attributes["Main-Class"] = mainClass
                    attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") { it.name }
                }

                doLast {
                    configurations.runtimeClasspath.get().forEach {
                        val file = File("$buildDir/libs/${it.name}")
                        if (!file.exists()) {
                            it.copyTo(file)
                        }
                    }
                }
            }
        }
    }
}

fun Project.mainClass() =
    "$group.${name.replace("-", "")}.AppKt"

fun Project.erFellesmodul() = name == "felles"
