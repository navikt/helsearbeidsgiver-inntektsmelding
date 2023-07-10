import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jmailen.kotlinter")
    id("maven-publish")
    id("java")
    id("jacoco")
    id("jacoco-report-aggregation")
    id("jvm-test-suite")
}

buildscript {
    repositories {
        mavenCentral()
    }
}

dependencies {
    subprojects.filter { it.name != "integrasjonstest" }
        .forEach {
            jacocoAggregation(project(":${it.name}"))
        }
}

allprojects {
    tasks {
        val jvmTargetVersion: String by project

        withType<KotlinCompile> {
            kotlinOptions.jvmTarget = jvmTargetVersion
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
        "org.jetbrains.kotlin.plugin.serialization",
        "org.jmailen.kotlinter",
        "maven-publish",
        "java",
        "jacoco"
    )

    tasks {
        if (!project.erFellesModul() && !project.erFellesTestModul() && !project.erDokumentModul()) {
            named<Jar>("jar") {
                archiveBaseName.set("app")

                val mainClass = project.mainClass()

                doLast {
                    if (project.name != "dokument") {
                        validateMainClassFound(mainClass)
                    }
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

        jacocoTestReport {
            dependsOn(test)
            reports {
                xml.required.set(true)
                html.required.set(true)
                csv.required.set(true)
                html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
            }
        }
    }

    val junitJupiterVersion: String by project
    val kotestVersion: String by project
    val kotlinCoroutinesVersion: String by project
    val kotlinSerializationVersion: String by project
    val mockkVersion: String by project
    val utilsVersion: String by project

    dependencies {
        if (!erDokumentModul()) {
            if (!erFellesModul()) {
                implementation(project(":felles"))
                implementation(project(":dokument"))
            }
            if (!erFellesTestModul()) {
                testImplementation(project(":felles-test"))
            }

            // dokument har inkompatibel java-versjon
            implementation("no.nav.helsearbeidsgiver:utils:$utilsVersion")
        }

        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")

        testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
        testImplementation("io.kotest:kotest-framework-datatest:$kotestVersion")
        testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
        testImplementation("io.mockk:mockk:$mockkVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")

        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    }
}

tasks {
    create("buildMatrix") {
        doLast {
            taskOutputJson(
                "project" to getBuildableProjects().toJsonList()
            )
        }
    }

    create("buildAllMatrix") {
        doLast {
            taskOutputJson(
                "project" to getBuildableProjects(buildAll = true).toJsonList()
            )
        }
    }

    create("deployMatrix") {
        deployMatrix()
    }

    create("deployMatrixDev") {
        deployMatrix(includeCluster = "dev-gcp")
    }

    create("deployMatrixProd") {
        deployMatrix(includeCluster = "prod-gcp", deployAll = true)
    }

    check {
        dependsOn(named<JacocoReport>("testCodeCoverageReport"))
    }
}

fun getBuildableProjects(buildAll: Boolean = false): List<String> {
    if (buildAll) return subprojects.map { it.name }
    val changedFiles = System.getenv("CHANGED_FILES")
        ?.takeIf(String::isNotBlank)
        ?.split(",")
        ?: throw IllegalStateException("Ingen endrede filer funnet.")

    val hasCommonChanges = changedFiles.any { it.startsWith("felles/") } ||
        changedFiles.containsAny(
            ".github/workflows/build.yml",
            "config/nais.yml",
            "build.gradle.kts",
            "Dockerfile",
            "gradle.properties",
            "spesifikasjon.yaml"
        )

    return subprojects.map { it.name }
        .let { projects ->
            if (hasCommonChanges) {
                projects
            } else {
                projects.filter { project ->
                    changedFiles.any {
                        it.startsWith("$project/") ||
                            it.startsWith("config/$project/")
                    }
                }
            }
        }
}

fun getDeployMatrixVariables(
    includeCluster: String? = null,
    deployAll: Boolean = false
): Triple<Set<String>, Set<String>, List<Pair<String, String>>> {
    val clustersByProject = getBuildableProjects(deployAll).associateWith { project ->
        File("config", project)
            .listFiles()
            ?.filter { it.isFile && it.name.endsWith(".yml") }
            ?.map { it.name.removeSuffix(".yml") }
            ?.let { clusters ->
                if (includeCluster != null) {
                    listOf(includeCluster).intersect(clusters.toSet())
                } else {
                    clusters
                }
            }
            ?.toSet()
            ?.ifEmpty { null }
    }
        .mapNotNull { (key, value) ->
            value?.let { key to it }
        }
        .toMap()

    val allClusters = clustersByProject.values.flatten().toSet()

    val exclusions = clustersByProject.flatMap { (project, clusters) ->
        allClusters.subtract(clusters)
            .map { Pair(project, it) }
    }

    return Triple(
        clustersByProject.keys,
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
        ?.any { it.path.contains(mainClassOsSpecific) }
        ?: false

    if (!mainClassFound) throw RuntimeException("Kunne ikke finne main class: $mainClass")
}

fun Project.mainClass(): String =
    "$group.${name.replace("-", "")}.AppKt"

fun Project.erFellesModul(): Boolean =
    name == "felles"

fun Project.erFellesTestModul(): Boolean =
    name == "felles-test"

fun Project.erDokumentModul(): Boolean =
    name == "dokument"

fun List<String>.containsAny(vararg others: String): Boolean =
    this.intersect(others.toSet()).isNotEmpty()

fun Task.deployMatrix(includeCluster: String? = null, deployAll: Boolean = false) {
    doLast {
        val (
            deployableProjects,
            clusters,
            exclusions
        ) = getDeployMatrixVariables(includeCluster, deployAll)

        taskOutputJson(
            "project" to deployableProjects.toJsonList(),
            "cluster" to clusters.toJsonList(),
            "exclude" to exclusions.map { (project, cluster) ->
                listOf(
                    "project" to project,
                    "cluster" to cluster
                )
                    .toJsonObject()
            }
                .toJsonList { it }
        )
    }
}

fun taskOutputJson(vararg keyValuePairs: Pair<String, String>) {
    keyValuePairs.toList()
        .toJsonObject { it }
        .let(::println)
}

fun Iterable<String>.toJsonList(
    transform: (String) -> String = { it.inQuotes() }
): String =
    joinToString(prefix = "[", postfix = "]", transform = transform)

fun Iterable<Pair<String, String>>.toJsonObject(
    transformValue: (String) -> String = { it.inQuotes() }
): String =
    joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "${key.inQuotes()}: ${transformValue(value)}"
    }

fun String.inQuotes(): String =
    "\"$this\""
