val kafkaClientVersion: String by project

plugins {
    id("java-test-fixtures")
}

dependencies {
    api("org.apache.kafka:kafka-clients:$kafkaClientVersion")
}
