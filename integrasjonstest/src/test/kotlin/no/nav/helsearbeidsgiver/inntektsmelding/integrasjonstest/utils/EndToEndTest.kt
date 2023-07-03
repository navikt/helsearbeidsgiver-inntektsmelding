package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.prometheus.client.CollectorRegistry
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.db.Database
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.PriProducer
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.buildApp
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock.mapHikariConfigByContainer
import no.nav.helsearbeidsgiver.utils.log.logger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
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

    val messages = Messages()

    val tilgangProducer by lazy { TilgangProducer(rapid) }
    val imRepository by lazy { InntektsmeldingRepository(database.db) }
    val forespoerselRepository by lazy { ForespoerselRepository(database.db) }

    val altinnClient = mockk<AltinnClient>()
    val arbeidsgiverNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>(relaxed = true)
    val dokarkivClient = mockk<DokArkivClient>(relaxed = true)
    lateinit var redisStore: RedisStore
    val priProducer = mockk<PriProducer>()

    private val om = customObjectMapper()

    @BeforeEach
    fun beforeEachEndToEnd() {
        messages.reset()
        clearAllMocks()
    }

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

    override fun onMessage(message: String, context: MessageContext) {
        logger.info("onMessage: $message")
        messages.add(message)
    }

    fun filter(event: EventName, behovType: BehovType? = null, datafelt: DataFelt? = null, løsning: Boolean = false): List<JsonNode> =
        messages.filter(event)
            .let {
                if (behovType != null) {
                    it.filter(behovType, løsning)
                } else {
                    it
                }
            }.let {
                if (datafelt != null) {
                    it.filter(datafelt)
                } else {
                    it
                }
            }
            .first()
            .toJsonNode()
            .let(::listOf)

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

    fun publishMessage(vararg messageFields: Pair<IKey, JsonElement>) {
        rapid.publish(*messageFields).also {
            println("Publiserte melding: $it")
        }
    }

    /** Avslutter venting dersom meldinger finnes og ingen nye ankommer i løpet av 1500 ms. */
    fun waitForMessages(millis: Long) {
        val startTime = System.nanoTime()

        var messageAmount = 0

        while (messageAmount == 0 || messageAmount != messages.all().size) {
            val elapsedTime = (System.nanoTime() - startTime) / 1_000_000
            if (elapsedTime > millis) {
                throw RuntimeException("Tid brukt på å vente på meldinger overskred grensen på $millis ms.")
            }

            messageAmount = messages.all().size

            Thread.sleep(1500)
        }
    }
}
