import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jmailen.kotlinter")
    id("maven-publish")
    java
    jacoco
    `jacoco-report-aggregation`
    `jvm-test-suite`
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.fasterxml.jackson.core:jackson-databind:2.13.4.2")
    }
}

dependencies {
    jacocoAggregation(project(":aareg"))
    jacocoAggregation(project(":altinn"))
    jacocoAggregation(project(":api"))
    jacocoAggregation(project(":brreg"))
    jacocoAggregation(project(":db"))
    jacocoAggregation(project(":distribusjon"))
    jacocoAggregation(project(":dokument"))
    jacocoAggregation(project(":felles"))
    jacocoAggregation(project(":forespoersel-mottatt"))
    jacocoAggregation(project(":helsebro"))
    jacocoAggregation(project(":innsending"))
    jacocoAggregation(project(":inntekt"))
    jacocoAggregation(project(":joark"))
    jacocoAggregation(project(":notifikasjon"))
    jacocoAggregation(project(":pdl"))
    jacocoAggregation(project(":preutfylt"))
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
        if (!project.erFellesModul() && !project.erFellesTestModul()) {
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
                csv.required.set(false)
                html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
            }
        }
    }

    val junitJupiterVersion: String by project
    val kotestVersion: String by project
    val kotlinCoroutinesVersion: String by project
    val kotlinSerializationVersion: String by project
    val mockkVersion: String by project

    dependencies {
        if (!erFellesModul()) {
            implementation(project(":felles"))
            implementation(project(":dokument"))
        }
        if (!erFellesTestModul() && project.name != "dokument") {
            testImplementation(project(":felles-test"))
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
    val mapper = ObjectMapper()

    create("buildMatrix") {
        doLast {
            mapper.taskOutput(
                "project" to getBuildableProjects()
            )
        }
    }

    create("buildAllMatrix") {
        doLast {
            mapper.taskOutput(
                "project" to getBuildableProjects(buildAll = true)
            )
        }
    }

    create("deployMatrix") {
        deployMatrix(mapper)
    }

    create("deployMatrixDev") {
        deployMatrix(mapper, includeCluster = "dev-gcp")
    }

    create("deployMatrixProd") {
        deployMatrix(mapper, includeCluster = "prod-gcp", deployAll = true)
    }
}

reporting {
    reports {
        val testCodeCoverageReport2 by creating(JacocoCoverageReport::class) {
            testType.set(TestSuiteType.UNIT_TEST)
        }
    }
}

tasks.check {
    dependsOn(tasks.named<JacocoReport>("testCodeCoverageReport"))
}

fun getBuildableProjects(buildAll: Boolean = false): List<String> {
    if (buildAll) return subprojects.map { it.name }.filter { it != "integrasjonstest" }
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

    return subprojects.map { it.name }.filter { it != "integrasjonstest" }
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
    deployAll: Boolean = false,
): Triple<Set<String>, Set<String>, List<Pair<String, String>>> {
    val clustersByProject = getBuildableProjects(deployAll).associateWith { project ->
        File("config", project)
            .listFiles()
            ?.filter { it.isFile && it.name.endsWith(".yml") }
            ?.map { it.name.removeSuffix(".yml") }
            ?.let { clusters ->
                if (includeCluster != null) {
                    listOf(includeCluster).intersect(clusters)
                } else {
                    clusters
                }
            }
            ?.toSet()
            ?.ifEmpty { null }
    }
        .mapNotNull { (key, value) ->
            if (value == null) {
                null
            } else {
                key to value
            }
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

fun Project.mainClass() =
    "$group.${name.replace("-", "")}.AppKt"

fun Project.erFellesModul() =
    name == "felles" || name == "dokument"

fun Project.erFellesTestModul() =
    name == "felles-test"

fun ObjectMapper.taskOutput(vararg keyValuePairs: Pair<String, Any>) {
    mapOf(*keyValuePairs)
        .let(this::writeValueAsString)
        .let(::println)
}

fun List<String>.containsAny(vararg others: String) =
    this.intersect(others.toSet()).isNotEmpty()

fun Task.deployMatrix(mapper: ObjectMapper, includeCluster: String? = null, deployAll: Boolean = false) {
    doLast {
        val (
            deployableProjects,
            clusters,
            exclusions,
        ) = getDeployMatrixVariables(includeCluster, deployAll)

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
