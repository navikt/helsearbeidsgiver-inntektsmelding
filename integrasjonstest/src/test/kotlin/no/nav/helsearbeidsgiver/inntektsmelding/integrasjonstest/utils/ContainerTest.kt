package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

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

abstract class ContainerTest {
    private val topic = "helsearbeidsgiver.inntektsmelding"

    private val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.1"))
    val redisContainer = RedisContainer(DockerImageName.parse("redis:7"))
    val postgreSQLContainerOne = PostgreSQLContainer<Nothing>("postgres:14").apply {
        setCommand("postgres", "-c", "fsync=off", "-c", "log_statement=all", "-c", "wal_level=logical")
    }
    val postgreSQLContainerTwo = PostgreSQLContainer<Nothing>("postgres:14").apply {
        setCommand("postgres", "-c", "fsync=off", "-c", "log_statement=all", "-c", "wal_level=logical")
    }

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
                        it.bootstrapServers
                    )
                }
            }
            .let(AdminClient::create)
            .createTopics(listOf(NewTopic(topic, 1, 1.toShort())))

        println("Starter Redis...")
        redisContainer.start()

        println("Starter Postgres...")
        postgreSQLContainerOne.start()
        postgreSQLContainerTwo.start()

        println("Containerne er klare!")
    }

    @AfterAll
    fun stopContainers() {
        println("Stopper containere...")
        kafkaContainer.stop()
        postgreSQLContainerOne.stop()
        redisContainer.stop()
        println("Containere er stoppet!")
    }
}
