package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.mockk
import io.prometheus.client.CollectorRegistry
import kotlinx.serialization.json.Json
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.db.Database
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.PriProducer
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.buildApp
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.filter.findMessage
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock.mapHikariConfigByContainer
import no.nav.helsearbeidsgiver.utils.log.logger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import kotlin.concurrent.thread

private const val NOTIFIKASJON_LINK = "notifikasjonLink"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class EndToEndTest : ContainerTest(), RapidsConnection.MessageListener {

    private lateinit var thread: Thread

    private val logger = logger()

    private val rapid by lazy {
        RapidApplication.create(
            mapOf(
                "KAFKA_RAPID_TOPIC" to TOPIC,
                "KAFKA_CREATE_TOPICS" to TOPIC,
                "RAPID_APP_NAME" to "HAG",
                "KAFKA_BOOTSTRAP_SERVERS" to kafkaContainer.bootstrapServers,
                "KAFKA_CONSUMER_GROUP_ID" to "HAG"
            )
        )
    }

    private val database by lazy {
        println("Database jdbcUrl: ${postgreSQLContainer.jdbcUrl}")
        postgreSQLContainer.let(::mapHikariConfigByContainer)
            .let(::Database)
            .also(Database::migrate)
    }

    val meldinger = mutableListOf<JsonNode>()
    val results = mutableListOf<String>()

    val tilgangProducer by lazy { TilgangProducer(rapid) }
    val imRepository by lazy { InntektsmeldingRepository(database.db) }
    val forespoerselRepository by lazy { ForespoerselRepository(database.db) }

    val altinnClient = mockk<AltinnClient>()
    val arbeidsgiverNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>(relaxed = true)
    val dokarkivClient = mockk<DokArkivClient>(relaxed = true)
    lateinit var redisStore: RedisStore
    val priProducer  = mockk<PriProducer>()

    private val om = customObjectMapper()

    var filterMessages: (JsonNode) -> Boolean = { true }

    @BeforeAll
    fun beforeAllEndToEnd() {
        redisStore = RedisStore(redisContainer.redisURI)

        rapid.buildApp(
            redisStore,
            database,
            imRepository,
            forespoerselRepository,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            dokarkivClient,
            mockk(relaxed = true),
            arbeidsgiverNotifikasjonKlient,
            NOTIFIKASJON_LINK,
            priProducer,
            altinnClient,
            mockk(relaxed = true)
        )
        rapid.register(this)
        thread = thread {
            rapid.start()
        }
        Thread.sleep(2000)
    }

    fun resetMessages() {
        meldinger.clear()
        results.clear()
    }

    override fun onMessage(message: String, context: MessageContext) {
        logger.info("onMessage: $message")
        if (filterMessages.invoke(customObjectMapper().readTree(message))) {
            results.add(message)
        }
        meldinger.add(Json.parseToJsonElement(message).toJsonNode())
    }

    fun filter(event: EventName, behovType: BehovType? = null, datafelt: DataFelt? = null, løsning: Boolean? = false): List<JsonNode> {
        return findMessage(meldinger, event, behovType, datafelt, løsning)
    }

    @AfterAll
    fun afterAllEndToEnd() {
        CollectorRegistry.defaultRegistry.clear()
        rapid.stop()
        thread.interrupt()
        logger.info("Stopped")
    }

    fun publish(value: Any) {
        val json = om.writeValueAsString(value)
        println("Publiserer melding: $json")
        rapid.publish(json)
    }

    fun getMessages(t: (JsonNode) -> Boolean): List<JsonNode> {
        return results.map { Json.parseToJsonElement(it).toJsonNode() }.filter(t).toList()
    }

    fun getMessage(index: Int): JsonNode {
        return Json.parseToJsonElement(results[index + 1]).toJsonNode()
    }

    fun getMessageCount(): Int {
        return results.size - 1
    }
}
