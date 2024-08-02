package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.brreg.Virksomhet
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.db.exposed.Database
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.createAaregRiver
import no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice.createAktiveOrgnrService
import no.nav.helsearbeidsgiver.inntektsmelding.altinn.createAltinn
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.brospinn.SpinnKlient
import no.nav.helsearbeidsgiver.inntektsmelding.brospinn.createHentEksternImRiver
import no.nav.helsearbeidsgiver.inntektsmelding.brospinn.createSpinnService
import no.nav.helsearbeidsgiver.inntektsmelding.brreg.createBrregRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.SelvbestemtImRepo
import no.nav.helsearbeidsgiver.inntektsmelding.db.createDbRivers
import no.nav.helsearbeidsgiver.inntektsmelding.distribusjon.createDistribusjonRiver
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart.createForespoerselBesvartFraSimba
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart.createForespoerselBesvartFraSpleis
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart.createMarkerForespoerselBesvart
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt.createForespoerselMottatt
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.createHelsebro
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.createInnsending
import no.nav.helsearbeidsgiver.inntektsmelding.inntekt.createHentInntektRiver
import no.nav.helsearbeidsgiver.inntektsmelding.inntektselvbestemtservice.createInntektSelvbestemtService
import no.nav.helsearbeidsgiver.inntektsmelding.inntektservice.createInntektService
import no.nav.helsearbeidsgiver.inntektsmelding.joark.createJournalfoerImRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.createNotifikasjonRivers
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.createNotifikasjonServices
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db.SelvbestemtRepo
import no.nav.helsearbeidsgiver.inntektsmelding.pdl.createPdlRiver
import no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice.createLagreSelvbestemtImService
import no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice.createTilgangService
import no.nav.helsearbeidsgiver.inntektsmelding.trengerservice.createHentForespoerselService
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.pdl.domene.FullPerson
import no.nav.helsearbeidsgiver.pdl.domene.PersonNavn
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

private const val NOTIFIKASJON_LINK = "notifikasjonLink"

val bjarneBetjent =
    FullPerson(
        navn =
            PersonNavn(
                fornavn = "Bjarne",
                mellomnavn = null,
                etternavn = "Betjent",
            ),
        foedselsdato = 28.mai,
        ident = Fnr.genererGyldig().verdi,
    )
val maxMekker =
    FullPerson(
        navn =
            PersonNavn(
                fornavn = "Max",
                mellomnavn = null,
                etternavn = "Mekker",
            ),
        foedselsdato = 6.august,
        ident = Fnr.genererGyldig().verdi,
    )

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class EndToEndTest : ContainerTest() {
    private val imTestRapid = ImTestRapid()

    private val inntektsmeldingDatabase by lazy {
        println("Database jdbcUrl for im-db: ${postgresContainerOne.jdbcUrl}")
        postgresContainerOne
            .toHikariConfig()
            .let(::Database)
            .also {
                val migrationLocation = Path("../db/src/main/resources/db/migration").absolutePathString()
                it.migrate(migrationLocation)
            }.createTruncateFunction()
    }

    private val notifikasjonDatabase by lazy {
        println("Database jdbcUrl for im-notifikasjon: ${postgresContainerTwo.jdbcUrl}")
        postgresContainerTwo
            .toHikariConfig()
            .let(::Database)
            .also {
                val migrationLocation = Path("../notifikasjon/src/main/resources/db/migration").absolutePathString()
                it.migrate(migrationLocation)
            }.createTruncateFunction()
    }

    // Vent på rediscontainer
    val redisConnection by lazy {
        repeat(5) {
            runCatching { RedisConnection(redisContainer.redisURI) }
                .onSuccess { return@lazy it }
                .onFailure { runBlocking { delay(1000) } }
        }
        throw IllegalStateException("Klarte ikke koble til Redis.")
    }

    val messages get() = imTestRapid.messages

    val tilgangProducer by lazy { TilgangProducer(imTestRapid) }

    val imRepository by lazy { InntektsmeldingRepository(inntektsmeldingDatabase.db) }
    val selvbestemtImRepo by lazy { SelvbestemtImRepo(inntektsmeldingDatabase.db) }
    val forespoerselRepository by lazy { ForespoerselRepository(inntektsmeldingDatabase.db) }

    private val selvbestemtRepo by lazy { SelvbestemtRepo(notifikasjonDatabase.db) }

    val altinnClient = mockk<AltinnClient>()
    val arbeidsgiverNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>(relaxed = true)
    val dokarkivClient = mockk<DokArkivClient>(relaxed = true)
    val spinnKlient = mockk<SpinnKlient>()
    val brregClient = mockk<BrregClient>(relaxed = true)
    val mockPriProducer = mockk<PriProducer>()
    val aaregClient = mockk<AaregClient>(relaxed = true)
    val inntektClient = mockk<InntektKlient>(relaxed = true)

    val pdlKlient = mockk<PdlClient>()

    @BeforeEach
    fun beforeEachEndToEnd() {
        imTestRapid.reset()
        clearAllMocks()

        coEvery { pdlKlient.personBolk(any()) } returns
            listOf(
                bjarneBetjent,
                maxMekker,
            )
        coEvery { brregClient.hentVirksomhetNavn(any()) } returns "Bedrift A/S"
        coEvery { brregClient.hentVirksomheter(any()) } answers {
            firstArg<List<String>>().map { orgnr ->
                Virksomhet(
                    organisasjonsnummer = orgnr,
                    navn = "Bedrift A/S",
                )
            }
        }
        coEvery { arbeidsgiverNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns "123456"
        coEvery { arbeidsgiverNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any()) } returns "654321"

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
        println("Starter rivers...")
        imTestRapid.apply {
            // Servicer
            createAktiveOrgnrService(redisConnection)
            createInnsending(redisConnection)
            createInntektService(redisConnection)
            createInntektSelvbestemtService(redisConnection)
            createLagreSelvbestemtImService(redisConnection)
            createNotifikasjonServices(redisConnection)
            createSpinnService()
            createTilgangService(redisConnection)
            createHentForespoerselService(redisConnection)

            // Rivers
            createAaregRiver(aaregClient)
            createAltinn(altinnClient)
            createBrregRiver(brregClient, false)
            createDbRivers(imRepository, selvbestemtImRepo, forespoerselRepository)
            createDistribusjonRiver(mockk(relaxed = true))
            createForespoerselBesvartFraSimba()
            createForespoerselBesvartFraSpleis(mockPriProducer)
            createForespoerselMottatt(mockPriProducer)
            createHelsebro(mockPriProducer)
            createHentEksternImRiver(spinnKlient)
            createHentInntektRiver(inntektClient)
            createJournalfoerImRiver(dokarkivClient)
            createMarkerForespoerselBesvart(mockPriProducer)
            createNotifikasjonRivers(NOTIFIKASJON_LINK, selvbestemtRepo, arbeidsgiverNotifikasjonKlient)
            createPdlRiver(pdlKlient)
        }
    }

    @AfterAll
    fun afterAllEndToEnd() {
        // Prometheus-metrikker spenner bein på testene uten denne
        CollectorRegistry.defaultRegistry.clear()
        redisConnection.close()
        inntektsmeldingDatabase.dataSource.close()
        notifikasjonDatabase.dataSource.close()
        println("Stopped.")
    }

    fun publish(vararg messageFields: Pair<Key, JsonElement>) {
        println("Publiserer melding med felt: ${messageFields.toMap()}")
        imTestRapid.publish(messageFields.toMap())
    }

    fun publish(vararg messageFields: Pair<Pri.Key, JsonElement>): JsonElement {
        println("Publiserer pri-melding med felt: ${messageFields.toMap()}")
        return messageFields
            .toMap()
            .mapKeys { (key, _) -> key.toString() }
            .toJson()
            .toString()
            .let {
                JsonMessage(it, MessageProblems(it), null)
            }.toJson()
            .also(imTestRapid::publish)
            .parseJson()
    }

    fun mockForespoerselSvarFraHelsebro(
        eventName: EventName,
        transaksjonId: UUID,
        forespoerselId: UUID,
        forespoerselSvar: ForespoerselSvar.Suksess,
    ) {
        every {
            mockPriProducer.send(
                *varargAny { (key, value) ->
                    key == Pri.Key.BEHOV &&
                        runCatching { value.fromJson(Pri.BehovType.serializer()) }.getOrNull() == Pri.BehovType.TRENGER_FORESPØRSEL
                },
            )
        } answers {
            publish(
                Pri.Key.BEHOV to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(Pri.BehovType.serializer()),
                Pri.Key.LØSNING to
                    ForespoerselSvar(
                        forespoerselId = forespoerselId,
                        resultat = forespoerselSvar,
                        boomerang =
                            mapOf(
                                Key.EVENT_NAME to eventName.toJson(),
                                Key.UUID to transaksjonId.toJson(),
                            ).toJson(),
                    ).toJson(ForespoerselSvar.serializer()),
            )

            Result.success(JsonObject(emptyMap()))
        }
    }

    fun RedisConnection.get(
        prefix: RedisPrefix,
        transaksjonId: UUID,
    ): String? = get("$prefix#$transaksjonId")

    fun truncateDatabase() {
        transaction(inntektsmeldingDatabase.db) {
            exec("SELECT truncate_tables()")
        }
        transaction(notifikasjonDatabase.db) {
            exec("SELECT truncate_tables()")
        }
    }
}

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
