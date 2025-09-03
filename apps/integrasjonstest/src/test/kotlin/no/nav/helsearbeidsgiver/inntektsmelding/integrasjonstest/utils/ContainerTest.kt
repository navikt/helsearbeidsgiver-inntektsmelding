package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import com.redis.testcontainers.RedisContainer
import no.nav.hag.simba.utils.db.exposed.test.postgresContainer
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.util.Properties

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ContainerTest {
    private val topic = "helsearbeidsgiver.inntektsmelding"

    private val kafkaContainer = ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"))
    val redisContainer = RedisContainer(DockerImageName.parse("redis:latest"))
    val postgresContainerOne = postgresContainer()
    val postgresContainerTwo = postgresContainer()

    @BeforeAll
    fun startContainers() {
        println("Starter containerne...")

        println("Starter Kafka...")
        kafkaContainer
            .also { it.start() }
            .let {
                Properties().apply {
                    setProperty(
                        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                        it.bootstrapServers,
                    )
                }
            }.let(AdminClient::create)
            .createTopics(listOf(NewTopic(topic, 1, 1.toShort())))

        println("Starter Redis...")
        redisContainer.start()

        println("Starter Postgres...")
        postgresContainerOne.start()
        postgresContainerTwo.start()

        println("Containerne er klare!")
    }

    @AfterAll
    fun stopContainers() {
        println("Stopper containere...")
        kafkaContainer.stop()
        postgresContainerOne.stop()
        postgresContainerTwo.stop()
        redisContainer.stop()
        println("Containere er stoppet!")
    }
}
