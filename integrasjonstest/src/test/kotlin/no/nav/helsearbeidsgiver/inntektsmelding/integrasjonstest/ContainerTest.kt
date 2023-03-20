package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import com.redis.testcontainers.RedisContainer
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.Properties

open class ContainerTest {
    // Containers
    val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.1"))
    val redisContainer = RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG))
    val postgreSQLContainer = PostgreSQLContainer<Nothing>("postgres:latest")

    val TOPIC = "helsearbeidsgiver.inntektsmelding"

    @BeforeAll
    fun startContainers() {
        logger.info("Starter Kafka...")
        kafkaContainer.start()
        val props = Properties()
        props.setProperty(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.bootstrapServers)
        val adminClient: AdminClient = AdminClient.create(props)
        adminClient.createTopics(listOf(NewTopic(TOPIC, 1, 1.toShort())))
        logger.info("Starter Redis...")
        redisContainer.start()
        logger.info("Startet Redis port: ${redisContainer.firstMappedPort}")
        logger.info("Starter Postgres...")
        postgreSQLContainer.start()
        logger.info("Startet Postgres...")
    }

    @AfterAll
    fun stopContainers() {
        logger.info("Stopping...")
        kafkaContainer.stop()
        postgreSQLContainer.stop()
        redisContainer.stop()
        logger.info("Stopped")
    }
}
