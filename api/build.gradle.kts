val ktorVersion: String by project
val altinnVersion: String by project
val valiktorVersion: String by project
val lettuceVersion: String by project
val kotlinSerializationVersion: String by project

plugins {
    kotlin("plugin.serialization") version "1.7.10"
}

dependencies {
    implementation(project(":felles"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")
    implementation("io.lettuce:lettuce-core:$lettuceVersion")
    implementation("org.valiktor:valiktor-core:$valiktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("no.nav.helsearbeidsgiver:altinn-client:$altinnVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation(kotlin("test"))
}
