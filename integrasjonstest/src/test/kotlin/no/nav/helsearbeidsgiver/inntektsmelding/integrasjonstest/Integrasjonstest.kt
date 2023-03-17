package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.redis.testcontainers.RedisContainer
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.inntektsmelding.akkumulator.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.db.Database
import no.nav.helsearbeidsgiver.inntektsmelding.db.Repository
import no.nav.helsearbeidsgiver.pdl.PdlClient
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.util.Properties
import kotlin.concurrent.thread

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class Integrasjonstest : RapidsConnection.MessageListener {
    val logger = LoggerFactory.getLogger(this::class.java)
    private val TOPIC = "helsearbeidsgiver.inntektsmelding"
    private lateinit var thread: Thread
    lateinit var rapid: RapidsConnection
    private val om = ObjectMapper()
    private var results: MutableList<String> = mutableListOf()

    // Containers
    private val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.1"))
    private val redisContainer = RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG))
    private val postgreSQLContainer = PostgreSQLContainer<Nothing>("postgres:latest")

    // Clients
    var pdlClient = mockk<PdlClient>()
    var aaregClient = mockk<AaregClient>()
    var brregClient = mockk<BrregClient>()
    var inntektKlient = mockk<InntektKlient>()
    var dokarkivClient = mockk<DokArkivClient>()
    var database = mockk<Database>()
    var repository = mockk<Repository>()
    var arbeidsgiverNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>()
    var notifikasjonLink = "notifikasjonLink"

    @BeforeAll
    fun beforeAll() {
        logger.info("Starter Kafka...")
        kafkaContainer.start()
        val props = Properties()
        props.setProperty(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.bootstrapServers)
        val adminClient: AdminClient = AdminClient.create(props)
        adminClient.createTopics(listOf(NewTopic(TOPIC, 1, 1.toShort())))
        logger.info("Starter Redis...")
        redisContainer
            .waitingFor(Wait.defaultWaitStrategy())
            .start()
        logger.info("Redis port: ${redisContainer.firstMappedPort}")
        logger.info("Starter Postgres...")
        postgreSQLContainer.start()
        val env = HashMap<String, String>().also {
            it.put("KAFKA_RAPID_TOPIC", TOPIC)
            it.put("KAFKA_CREATE_TOPICS", TOPIC)
            it.put("RAPID_APP_NAME", "HAG")
            it.put("KAFKA_BOOTSTRAP_SERVERS", kafkaContainer.bootstrapServers)
            it.put("KAFKA_CONSUMER_GROUP_ID", "HAG")
        }
        // Klienter
        val redisStore = RedisStore(redisContainer.redisURI)
        // Rapids
        rapid = RapidApplication.create(env).buildApp(
            redisStore,
            database,
            repository,
            aaregClient,
            brregClient,
            inntektKlient,
            dokarkivClient,
            pdlClient,
            arbeidsgiverNotifikasjonKlient,
            notifikasjonLink
        )
        rapid.register(this)
        thread = thread {
            rapid.start()
        }
        Thread.sleep(2000)
    }

    override fun onMessage(message: String, context: MessageContext) {
        logger.info("onMessage: $message")
        if (results.size == 1) {
            results.clear() // Filtrerer vekk den første meldingen da den er det vi faktisk sender inn
        }
        results.add(message)
    }

    @AfterAll
    fun afterAll() {
        logger.info("Stopping...")
        kafkaContainer.stop()
        postgreSQLContainer.stop()
        redisContainer.stop()
        // rapid.stop()
        thread.stop()
        logger.info("Stopped")
    }

    fun publish(value: Any) {
        rapid.publish(om.writeValueAsString(value))
    }

    fun getMessage(index: Int): JsonNode {
        return Json.parseToJsonElement(results[index]).toJsonNode()
    }

    fun getMessageCount(): Int {
        return results.size
    }
}
