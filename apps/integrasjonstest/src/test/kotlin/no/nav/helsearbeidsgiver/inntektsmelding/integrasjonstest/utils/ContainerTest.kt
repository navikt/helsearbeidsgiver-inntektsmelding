package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import com.redis.testcontainers.RedisContainer
import no.nav.hag.simba.utils.db.exposed.test.postgresContainer
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.lifecycle.Startables
import java.util.Properties

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ContainerTest {
    private val topic = "helsearbeidsgiver.inntektsmelding"

    private val kafkaContainer = KafkaContainer("apache/kafka-native:latest").withStartupAttempts(3)
    val redisContainer = RedisContainer("redis:latest")
    val postgresContainerOne = postgresContainer()
    val postgresContainerTwo = postgresContainer()

    @BeforeAll
    fun startContainers() {
        println("Starter containerne...")

        Startables
            .deepStart(
                kafkaContainer,
                redisContainer,
                postgresContainerOne,
                postgresContainerTwo,
            ).join()

        kafkaContainer
            .let {
                Properties().apply {
                    setProperty(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, it.bootstrapServers)
                }
            }.let(AdminClient::create)
            .createTopics(listOf(NewTopic(topic, 1, 1.toShort())))

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
