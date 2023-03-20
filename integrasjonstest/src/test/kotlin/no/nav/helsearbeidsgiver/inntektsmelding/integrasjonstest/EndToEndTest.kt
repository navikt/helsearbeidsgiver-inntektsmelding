package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.zaxxer.hikari.HikariDataSource
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
import no.nav.helsearbeidsgiver.inntektsmelding.db.DatabaseConfig
import no.nav.helsearbeidsgiver.inntektsmelding.db.Repository
import no.nav.helsearbeidsgiver.pdl.PdlClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.concurrent.thread

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class EndToEndTest : ContainerTest(), RapidsConnection.MessageListener {
    val logger = LoggerFactory.getLogger(this::class.java)
    private lateinit var thread: Thread
    lateinit var rapid: RapidsConnection
    private val om = ObjectMapper()
    private var results: MutableList<String> = mutableListOf()

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

    fun getDatabase(postgreSQLContainer: PostgreSQLContainer<Nothing>): Database {
        val config = DatabaseConfig(
            postgreSQLContainer.host,
            postgreSQLContainer.firstMappedPort.toString(),
            postgreSQLContainer.databaseName,
            postgreSQLContainer.username,
            this.postgreSQLContainer.password
        )
        println("Using db config: ${config}")
        val ds = HikariDataSource(config.dbConfig())
        val db = org.jetbrains.exposed.sql.Database.connect(ds)
        return Database(config)
    }

    @BeforeAll
    fun beforeAll() {
        val env = HashMap<String, String>().also {
            it.put("KAFKA_RAPID_TOPIC", TOPIC)
            it.put("KAFKA_CREATE_TOPICS", TOPIC)
            it.put("RAPID_APP_NAME", "HAG")
            it.put("KAFKA_BOOTSTRAP_SERVERS", kafkaContainer.bootstrapServers)
            it.put("KAFKA_CONSUMER_GROUP_ID", "HAG")
        }
        // Klienter
        val redisStore = RedisStore(redisContainer.redisURI)

        val config = DatabaseConfig(
            postgreSQLContainer.host,
            postgreSQLContainer.firstMappedPort.toString(),
            postgreSQLContainer.databaseName,
            postgreSQLContainer.username,
            postgreSQLContainer.password
        )
        println("Using db config: ${config}")
        val ds = HikariDataSource(config.dbConfig())
        database = Database(config)


        val db = org.jetbrains.exposed.sql.Database.connect(ds)
        repository = Repository(db)

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
        println("onMessage: $message")
        results.add(message)
    }

    @AfterAll
    fun afterAll() {
        thread.stop()
        println("Stopped")
    }

    fun publish(value: Any) {
        rapid.publish(om.writeValueAsString(value))
    }

    fun getMessage(index: Int): JsonNode {
        return Json.parseToJsonElement(results[index + 1]).toJsonNode()
    }

    fun getMessageCount(): Int {
        return results.size - 1
    }
}
