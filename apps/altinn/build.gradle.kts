dependencies {
    val altinnClientVersion: String by project
    val mockwebserverVersion: String by project
    val utilsVersion: String by project

    implementation(project(":felles-auth"))
    implementation("no.nav.helsearbeidsgiver:altinn-client:$altinnClientVersion")
    implementation("no.nav.helsearbeidsgiver:utils:$utilsVersion")

    testImplementation("com.squareup.okhttp3:mockwebserver:$mockwebserverVersion")
}
