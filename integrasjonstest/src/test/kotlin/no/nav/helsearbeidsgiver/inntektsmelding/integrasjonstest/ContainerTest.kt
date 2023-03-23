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
    val postgreSQLContainer = PostgreSQLContainer<Nothing>("postgres:14")

    val TOPIC = "helsearbeidsgiver.inntektsmelding"

    @BeforeAll
    fun startContainers() {
        println("Starter containerne...")
        println("Starter Kafka...")
        kafkaContainer.start()
        val props = Properties()
        props.setProperty(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.bootstrapServers)
        val adminClient: AdminClient = AdminClient.create(props)
        adminClient.createTopics(listOf(NewTopic(TOPIC, 1, 1.toShort())))
        println("Starter Redis...")
        redisContainer.start()
        println("Starter Postgres...")
        postgreSQLContainer.start()
        println("Containerne er klare!")
    }

    @AfterAll
    fun stopContainers() {
        println("Stopper containere...")
        kafkaContainer.stop()
        postgreSQLContainer.stop()
        redisContainer.stop()
        println("Containere er stoppet!")
    }
}
