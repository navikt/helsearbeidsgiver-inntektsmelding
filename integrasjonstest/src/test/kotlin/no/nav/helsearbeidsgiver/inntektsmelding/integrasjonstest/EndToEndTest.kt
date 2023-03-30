package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.mockk
import io.mockk.mockkStatic
import io.prometheus.client.CollectorRegistry
import kotlinx.serialization.json.Json
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.inntektsmelding.db.Database
import no.nav.helsearbeidsgiver.inntektsmelding.db.Repository
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.RedisStore
import no.nav.helsearbeidsgiver.pdl.PdlClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import kotlin.concurrent.thread

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class EndToEndTest : ContainerTest(), RapidsConnection.MessageListener {

    lateinit var rapid: RapidsConnection
    private val om = customObjectMapper()
    var results: MutableList<String> = mutableListOf()
    private lateinit var thread: Thread

    // Clients
    var pdlClient = mockk<PdlClient>()
    var aaregClient = mockk<AaregClient>()
    var brregClient = mockk<BrregClient>()
    var inntektKlient = mockk<InntektKlient>()
    var dokarkivClient = mockk<DokArkivClient>()
    val placeholderSak = mockkStatic("no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.OpprettNySakKt")
    val placeholderOppgave = mockkStatic("no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.OpprettOppgaveKt")
    val placeholderNyStatusSak = mockkStatic("no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.NyStatusSakByGrupperingsidKt")
    val placeholderOppgave2 = mockkStatic("no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.OppgaveUtfoertKt")
    var arbeidsgiverNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>()
    var notifikasjonLink = "notifikasjonLink"
    var filterMessages: (JsonNode) -> Boolean = { true }

    // Database
    var database = mockk<Database>()
    var repository = mockk<Repository>()

    @BeforeAll
    fun beforeAllEndToEnd() {
        val env = HashMap<String, String>().also {
            it.put("KAFKA_RAPID_TOPIC", TOPIC)
            it.put("KAFKA_CREATE_TOPICS", TOPIC)
            it.put("RAPID_APP_NAME", "HAG")
            it.put("KAFKA_BOOTSTRAP_SERVERS", kafkaContainer.bootstrapServers)
            it.put("KAFKA_CONSUMER_GROUP_ID", "HAG")
        }
        // Klienter
        val redisStore = RedisStore(redisContainer.redisURI)

        // Databasen - konfig må gjøres her ETTER at postgreSQLContainer er startet
        val config = mapHikariConfigByContainer(postgreSQLContainer)

        println("Database: jdbcUrl: ${config.jdbcUrl}")
        database = Database(config)
        repository = Repository(database.db)
        database.migrate()

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
        if (filterMessages.invoke(customObjectMapper().readTree(message))) {
            results.add(message)
        }
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
