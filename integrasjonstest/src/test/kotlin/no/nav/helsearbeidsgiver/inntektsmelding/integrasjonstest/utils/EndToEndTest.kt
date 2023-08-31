package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

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
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.createAareg
import no.nav.helsearbeidsgiver.inntektsmelding.altinn.createAltinn
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.brreg.createBrreg
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.Database
import no.nav.helsearbeidsgiver.inntektsmelding.db.createDb
import no.nav.helsearbeidsgiver.inntektsmelding.distribusjon.createDistribusjon
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart.createForespoerselBesvart
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt.createForespoerselMottatt
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.createHelsebro
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.createInnsending
import no.nav.helsearbeidsgiver.inntektsmelding.inntekt.createInntekt
import no.nav.helsearbeidsgiver.inntektsmelding.inntektservice.createInntektService
import no.nav.helsearbeidsgiver.inntektsmelding.joark.createJoark
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.createNotifikasjon
import no.nav.helsearbeidsgiver.inntektsmelding.pdl.createPdl
import no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice.createTilgangService
import no.nav.helsearbeidsgiver.inntektsmelding.trengerservice.createTrengerService
import no.nav.helsearbeidsgiver.pdl.PdlClient
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
                "KAFKA_RAPID_TOPIC" to topic,
                "KAFKA_CREATE_TOPICS" to topic,
                "RAPID_APP_NAME" to "HAG",
                "KAFKA_BOOTSTRAP_SERVERS" to kafkaContainer.bootstrapServers,
                "KAFKA_CONSUMER_GROUP_ID" to "HAG"
            )
        )
    }

    private val database by lazy {
        println("Database jdbcUrl: ${postgreSQLContainer.jdbcUrl}")
        postgreSQLContainer.toHikariConfig()
            .let(::Database)
            .also(Database::migrate)
    }

    val redisStore by lazy {
        RedisStore(redisContainer.redisURI)
    }

    val messages = Messages()

    val tilgangProducer by lazy { TilgangProducer(rapid) }
    val imRepository by lazy { InntektsmeldingRepository(database.db) }
    val forespoerselRepository by lazy { ForespoerselRepository(database.db) }

    val altinnClient = mockk<AltinnClient>()
    val pdlClient = mockk<PdlClient>(relaxed = true)
    val arbeidsgiverNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>(relaxed = true)
    val dokarkivClient = mockk<DokArkivClient>(relaxed = true)

    @BeforeEach
    fun beforeEachEndToEnd() {
        messages.reset()
        clearAllMocks()
    }

    @BeforeAll
    fun beforeAllEndToEnd() {
        // Start løsere
        logger.info("Starter løsere...")
        rapid.apply {
            createInnsending(redisStore)
            createInntektService(redisStore)
            createTilgangService(redisStore)
            createTrengerService(redisStore)

            createAareg(mockk(relaxed = true))
            createAltinn(altinnClient)
            createBrreg(mockk(relaxed = true), true)
            createDb(database, imRepository, forespoerselRepository)
            createDistribusjon(mockk(relaxed = true))
            createForespoerselBesvart(mockk(relaxed = true))
            createForespoerselMottatt(mockk(relaxed = true))
            createHelsebro(mockk(relaxed = true))
            createInntekt(mockk(relaxed = true))
            createJoark(dokarkivClient)
            createNotifikasjon(redisStore, arbeidsgiverNotifikasjonKlient, NOTIFIKASJON_LINK)
            createPdl(pdlClient)
        }
            .register(this)

        thread = thread {
            rapid.start()
        }
        Thread.sleep(2000)
    }

    override fun onMessage(message: String, context: MessageContext) {
        logger.info("onMessage: $message")
        messages.add(message)
    }

    @AfterAll
    fun afterAllEndToEnd() {
        // Prometheus-metrikker spenner bein på testene uten denne
        CollectorRegistry.defaultRegistry.clear()
        rapid.stop()
        thread.interrupt()
        logger.info("Stopped")
    }

    fun publish(vararg messageFields: Pair<IKey, JsonElement>) {
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
                throw MessagesWaitLimitException(millis)
            }

            messageAmount = messages.all().size

            Thread.sleep(1500)
        }
    }
}

private class MessagesWaitLimitException(millis: Long) : RuntimeException(
    "Tid brukt på å vente på meldinger overskred grensen på $millis ms."
)
