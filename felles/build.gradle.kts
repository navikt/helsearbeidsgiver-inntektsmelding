val lettuceVersion: String by project
val rapidsAndRiversVersion: String by project
val slf4jVersion: String by project

dependencies {
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    api("org.slf4j:slf4j-api:$slf4jVersion")

    implementation("io.lettuce:lettuce-core:$lettuceVersion")
}
