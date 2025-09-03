import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jmailen.kotlinter")
    id("java")
    id("jacoco")
    id("jacoco-report-aggregation")
    id("jvm-test-suite")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

allprojects {
    repositories {
        val githubPassword: String by project
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

    applyPlugins(
        "org.jetbrains.kotlin.jvm",
        "org.jetbrains.kotlin.plugin.serialization",
        "org.jmailen.kotlinter",
        "java",
        "jacoco",
    )

    tasks {
        register<DependencyReportTask>("allDependencies") {}

        withType<Test> {
            useJUnitPlatform()
            testLogging {
                events("skipped", "failed")
            }
        }

        if (!project.erUtilsModul()) {
            named<Jar>("jar") {
                archiveBaseName.set("app")

                val mainClass = project.mainClass()
                val dependencies = configurations.runtimeClasspath.get()

                doLast {
                    validateMainClassFound(mainClass)
                }

                manifest {
                    attributes["Main-Class"] = mainClass
                    attributes["Class-Path"] = dependencies.joinToString(separator = " ") { it.name }
                }

                doLast {
                    dependencies.forEach {
                        val file =
                            layout.buildDirectory
                                .file("libs/${it.name}")
                                .get()
                                .asFile
                        if (!file.exists()) {
                            it.copyTo(file)
                        }
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

    val hagDomeneInntektsmeldingVersion: String by project
    val junitJupiterVersion: String by project
    val kotestVersion: String by project
    val kotlinCoroutinesVersion: String by project
    val kotlinSerializationVersion: String by project
    val mockkVersion: String by project
    val utilsVersion: String by project

    dependencies {
        // Sjekk om disse er nødvendige ved oppgradering av pakker
        constraints {
            implementation("commons-beanutils:commons-beanutils:1.11.0") {
                because("helsearbeidsgiver-kontrakt-inntektsmelding")
            }
            implementation("io.ktor:ktor-client-core-jvm:2.3.13") {
                because("hag-bakgrunnsjobb")
            }
            implementation("org.apache.commons:commons-lang3:3.18.0") {
                because("helsearbeidsgiver-kontrakt-inntektsmelding")
            }
            implementation("org.apache.httpcomponents.client5:httpclient5:5.4.4") {
                because("brreg-client")
            }
            implementation("org.apache.kafka:kafka-clients:3.9.1") {
                because("rapids-and-rivers")
            }

            testImplementation("org.apache.commons:commons-compress:1.26.2") {
                because("kafka, testcontainers-redis, postgresql")
            }
        }

        if (!erUtilsFellesModul()) {
            implementation(project(":utils-felles"))
            testImplementation(testFixtures(project(":utils-felles")))

            if (!erUtilsModul()) {
                implementation(project(":utils-rapids-and-rivers"))
                testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
            }
        }

        implementation("no.nav.helsearbeidsgiver:domene-inntektsmelding:$hagDomeneInntektsmeldingVersion")
        implementation("no.nav.helsearbeidsgiver:utils:$utilsVersion")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")

        testImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
        testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
        testImplementation("io.kotest:kotest-framework-datatest:$kotestVersion")
        testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
        testImplementation("io.mockk:mockk:$mockkVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")

        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    }
}

dependencies {
    subprojects
        .filterNot { it.erIntegrasjonstestModul() }
        .forEach {
            jacocoAggregation(project(":${it.name}"))
        }
}

tasks {
    register("buildMatrix") {
        doLast {
            taskOutputJson(
                "project" to getBuildableProjects().toJsonList(),
            )
        }
    }

    register("deployMatrixDev") {
        deployMatrix(includeCluster = "dev-gcp")
    }

    register("deployMatrixProd") {
        deployMatrix(includeCluster = "prod-gcp")
    }

    check {
        dependsOn(named<JacocoReport>("testCodeCoverageReport"))
    }
}

fun getBuildableProjects(): List<String> {
    val testfilRegex = Regex("^(?:apps|utils)/[\\w-]+/src/test(?:Fixtures)?.+")

    val changedFiles =
        System
            .getenv("CHANGED_FILES")
            ?.takeIf(String::isNotBlank)
            ?.split(",")
            ?.filterNot(testfilRegex::matches)
            ?: throw IllegalStateException("Ingen endrede filer funnet.")

    val hasCommonChanges =
        changedFiles.any { file ->
            val hasUtilsChanges =
                setOf(
                    "auth",
                    "felles",
                    "rapids-and-rivers",
                ).flatMap {
                    setOf(
                        "utils/$it/build.gradle.kts",
                        "utils/$it/gradle.properties",
                        "utils/$it/src/main/",
                    )
                }.any { file.startsWith(it) }

            hasUtilsChanges ||
                file in
                listOf(
                    "Dockerfile",
                    "build.gradle.kts",
                    "settings.gradle.kts",
                    "gradle.properties",
                )
        }

    return subprojects
        .filterNot { it.erIntegrasjonstestModul() }
        .map { it.name }
        .let { projects ->
            if (hasCommonChanges) {
                projects
            } else {
                projects.filter { project ->
                    val dirName = project.removePrefix("apps-")
                    changedFiles.any {
                        it.startsWith("apps/$dirName/") ||
                            it.startsWith("config/$dirName/")
                    }
                }
            }
        }
}

fun getDeployMatrixVariables(includeCluster: String): Triple<Set<String>, Set<String>, List<Pair<String, String>>> {
    val clustersByProject =
        getBuildableProjects()
            .associateWith { project ->
                File("config", project)
                    .listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".yml") }
                    ?.map { it.name.removeSuffix(".yml") }
                    ?.let { clusters ->
                        setOf(includeCluster).intersect(clusters.toSet())
                    }?.ifEmpty { null }
            }.mapNotNull { (key, value) ->
                value?.let { key to it }
            }.toMap()

    val allClusters = clustersByProject.values.flatten().toSet()

    val exclusions =
        clustersByProject.flatMap { (project, clusters) ->
            allClusters
                .subtract(clusters)
                .map { Pair(project, it) }
        }

    return Triple(
        clustersByProject.keys,
        allClusters,
        exclusions,
    )
}

fun PluginAware.applyPlugins(vararg ids: String) {
    ids.forEach {
        apply(plugin = it)
    }
}

fun Task.validateMainClassFound(mainClass: String) {
    val mainClassOsSpecific = mainClass.replace(".", File.separator)

    val mainClassFound =
        this.project.sourceSets
            .findByName("main")
            ?.output
            ?.classesDirs
            ?.asFileTree
            ?.any { it.path.contains(mainClassOsSpecific) }
            ?: false

    if (!mainClassFound) throw RuntimeException("Kunne ikke finne main class: $mainClass")
}

fun Project.mainClass(): String {
    val validName = name.removePrefix("apps-").replace("-", "")
    return "$group.$validName.AppKt"
}

fun Project.erUtilsModul(): Boolean = name.startsWith("utils-")

fun Project.erUtilsFellesModul(): Boolean = name == "utils-felles"

fun Project.erIntegrasjonstestModul(): Boolean = name == "apps-integrasjonstest"

fun Task.deployMatrix(includeCluster: String) {
    doLast {
        val (
            deployableProjects,
            clusters,
            exclusions,
        ) = getDeployMatrixVariables(includeCluster)

        taskOutputJson(
            "project" to deployableProjects.toJsonList(),
            "cluster" to clusters.toJsonList(),
            "exclude" to
                exclusions
                    .map { (project, cluster) ->
                        listOf(
                            "project" to project,
                            "cluster" to cluster,
                        ).toJsonObject()
                    }.toJsonList { it },
        )
    }
}

fun taskOutputJson(vararg keyValuePairs: Pair<String, String>) {
    keyValuePairs
        .toList()
        .toJsonObject { it }
        .let(::println)
}

fun Iterable<String>.toJsonList(transform: (String) -> String = { it.inQuotes() }): String = joinToString(prefix = "[", postfix = "]", transform = transform)

fun Iterable<Pair<String, String>>.toJsonObject(transformValue: (String) -> String = { it.inQuotes() }): String =
    joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "${key.inQuotes()}: ${transformValue(value)}"
    }

fun String.inQuotes(): String = "\"$this\""
