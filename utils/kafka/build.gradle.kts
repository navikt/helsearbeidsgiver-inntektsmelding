val kafkaClientVersion: String by project

plugins {
    id("java-test-fixtures")
}

dependencies {
    api(project(":kontrakt-kafkatopic-pri"))
    api(project(":utils-felles"))
    api("org.apache.kafka:kafka-clients:$kafkaClientVersion")

    testImplementation(testFixtures(project(":utils-felles")))
}
