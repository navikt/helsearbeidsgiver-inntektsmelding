package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.prometheus.client.CollectorRegistry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.createAareg
import no.nav.helsearbeidsgiver.inntektsmelding.altinn.createAltinn
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.brospinn.SpinnKlient
import no.nav.helsearbeidsgiver.inntektsmelding.brospinn.createEksternInntektsmeldingLoeser
import no.nav.helsearbeidsgiver.inntektsmelding.brospinn.createSpinnService
import no.nav.helsearbeidsgiver.inntektsmelding.brreg.createBrreg
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.Database
import no.nav.helsearbeidsgiver.inntektsmelding.db.createDb
import no.nav.helsearbeidsgiver.inntektsmelding.distribusjon.createDistribusjon
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart.createForespoerselBesvartFraSimba
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart.createForespoerselBesvartFraSpleis
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart.createMarkerForespoerselBesvart
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
import no.nav.helsearbeidsgiver.pdl.domene.FullPerson
import no.nav.helsearbeidsgiver.pdl.domene.PersonNavn
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.mai
import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.sql.transactions.transaction
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
            .createTruncateFunction()
    }

    val redisStore by lazy {
        RedisStore(redisContainer.redisURI)
    }

    val messages = Messages()

    val tilgangProducer by lazy { TilgangProducer(rapid) }
    val imRepository by lazy { InntektsmeldingRepository(database.db) }
    val forespoerselRepository by lazy { ForespoerselRepository(database.db) }

    val altinnClient = mockk<AltinnClient>()
    val arbeidsgiverNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>(relaxed = true)
    val dokarkivClient = mockk<DokArkivClient>(relaxed = true)
    val spinnKlient = mockk<SpinnKlient>()
    val brregClient = mockk<BrregClient>(relaxed = true)
    val mockPriProducer = mockk<PriProducer>()

    private val pdlKlient = mockk<PdlClient>()

    @BeforeEach
    fun beforeEachEndToEnd() {
        messages.reset()
        clearAllMocks()

        coEvery { pdlKlient.personBolk(any()) } returns listOf(
            FullPerson(
                navn = PersonNavn(
                    fornavn = "Bjarne",
                    mellomnavn = null,
                    etternavn = "Betjent"
                ),
                foedselsdato = 28.mai,
                ident = "fnr-bjarne"
            ),
            FullPerson(
                navn = PersonNavn(
                    fornavn = "Max",
                    mellomnavn = null,
                    etternavn = "Mekker"
                ),
                foedselsdato = 6.august,
                ident = "fnr-max"
            )
        )
        coEvery { brregClient.hentVirksomhetNavn(any()) } returns "Bedrift A/S"
        coEvery { arbeidsgiverNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns "123456"
        coEvery { arbeidsgiverNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any()) } returns "654321"

        mockPriProducer.apply {
            // Må bare returnere en Result med gyldig JSON
            val emptyResult = Result.success(JsonObject(emptyMap()))
            every { send(any<JsonElement>()) } returns emptyResult
            every { send(*anyVararg<Pair<Pri.Key, JsonElement>>()) } returns emptyResult
        }
    }

    @BeforeAll
    fun beforeAllEndToEnd() {
        // Start rivers
        logger.info("Starter rivers...")
        rapid.apply {
            createInnsending(redisStore)
            createInntektService(redisStore)
            createTilgangService(redisStore)
            createTrengerService(redisStore)

            createAareg(mockk(relaxed = true))
            createAltinn(altinnClient)
            createBrreg(brregClient, false)
            createDb(database, imRepository, forespoerselRepository)
            createDistribusjon(mockk(relaxed = true))
            createForespoerselBesvartFraSimba()
            createForespoerselBesvartFraSpleis(mockPriProducer)
            createForespoerselMottatt(mockPriProducer)
            createMarkerForespoerselBesvart(mockPriProducer)
            createHelsebro(mockPriProducer)
            createInntekt(mockk(relaxed = true))
            createJoark(dokarkivClient)
            createNotifikasjon(redisStore, arbeidsgiverNotifikasjonKlient, NOTIFIKASJON_LINK)
            createPdl(pdlKlient)
            createEksternInntektsmeldingLoeser(spinnKlient)
            createSpinnService(redisStore)
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

    fun publish(vararg messageFields: Pair<Key, JsonElement>) {
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

    fun truncateDatabase() {
        transaction(database.db) {
            exec("SELECT truncate_tables()")
        }
    }
}

private class MessagesWaitLimitException(millis: Long) : RuntimeException(
    "Tid brukt på å vente på meldinger overskred grensen på $millis ms."
)

private fun Database.createTruncateFunction() =
    also {
        @Language("PostgreSQL")
        val query = """
            CREATE OR REPLACE FUNCTION truncate_tables() RETURNS void AS $$
            DECLARE
            truncate_statement text;
            BEGIN
                SELECT 'TRUNCATE ' || string_agg(format('%I.%I', schemaname, tablename), ',') || ' RESTART IDENTITY CASCADE'
                    INTO truncate_statement
                FROM pg_tables
                WHERE schemaname='public';

                EXECUTE truncate_statement;
            END;
            $$ LANGUAGE plpgsql;
        """

        transaction(db) {
            exec(query)
        }
    }
