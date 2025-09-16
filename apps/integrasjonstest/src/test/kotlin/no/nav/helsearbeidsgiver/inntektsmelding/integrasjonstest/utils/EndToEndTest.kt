package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import io.kotest.matchers.nulls.shouldNotBeNull
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.db.exposed.Database
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.ForespoerselFraBro
import no.nav.hag.simba.utils.felles.pritopic.Pri
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.rr.test.ImTestRapid
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.utils.bakgrunnsjobb.PostgresBakgrunnsjobbRepository
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.altinn.Altinn3M2MClient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.createAaregRiver
import no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice.createAktiveOrgnrService
import no.nav.helsearbeidsgiver.inntektsmelding.altinn.createAltinn
import no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice.createBerikInntektsmeldingService
import no.nav.helsearbeidsgiver.inntektsmelding.brospinn.SpinnKlient
import no.nav.helsearbeidsgiver.inntektsmelding.brospinn.createHentEksternImRiver
import no.nav.helsearbeidsgiver.inntektsmelding.brreg.createHentOrganisasjonNavnRiver
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.SelvbestemtImRepo
import no.nav.helsearbeidsgiver.inntektsmelding.db.createDbRivers
import no.nav.helsearbeidsgiver.inntektsmelding.distribusjon.createDistribusjonRiver
import no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.createFeilLytter
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart.createForespoerselEventSwitch
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.createHelsebroRivers
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselListeSvar
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.createInnsendingServices
import no.nav.helsearbeidsgiver.inntektsmelding.inntekt.createHentInntektRiver
import no.nav.helsearbeidsgiver.inntektsmelding.inntektselvbestemtservice.createInntektSelvbestemtService
import no.nav.helsearbeidsgiver.inntektsmelding.inntektservice.createInntektService
import no.nav.helsearbeidsgiver.inntektsmelding.joark.createJournalfoerImRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.createNotifikasjonRivers
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.createNotifikasjonServices
import no.nav.helsearbeidsgiver.inntektsmelding.pdl.createHentPersonerRiver
import no.nav.helsearbeidsgiver.inntektsmelding.selvbestemthentimservice.createHentSelvbestemtImService
import no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice.createLagreSelvbestemtImService
import no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice.createTilgangServices
import no.nav.helsearbeidsgiver.inntektsmelding.trengerservice.createHentForespoerselServices
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.pdl.domene.FullPerson
import no.nav.helsearbeidsgiver.pdl.domene.PersonNavn
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

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

    // Vent på inntektsmeldingdatabase
    private val inntektsmeldingDatabase by lazy {
        println("Database jdbcUrl for im-db: ${postgresContainerOne.jdbcUrl}")

        withRetries(
            feilmelding = "Klarte ikke sette opp inntektsmeldingDatabase.",
        ) {
            postgresContainerOne
                .toHikariConfig()
                .let(::Database)
                .also {
                    val migrationLocation = Path("../db/src/main/resources/db/migration").absolutePathString()
                    it.migrate(migrationLocation)
                }.createTruncateFunction()
        }
    }

    // Vent på feilbehandlerdatabase
    private val bakgrunnsjobbDatabase by lazy {
        println("Database jdbcUrl for im-feil-behandler: ${postgresContainerTwo.jdbcUrl}")

        withRetries(
            feilmelding = "Klarte ikke sette opp feilbehandlerdatabase.",
        ) {
            postgresContainerTwo
                .toHikariConfig()
                .let(::Database)
                .also {
                    val migrationLocation = Path("../feil-behandler/src/main/resources/db/migration").absolutePathString()
                    it.migrate(migrationLocation)
                }.createTruncateFunction()
        }
    }

    // Vent på rediscontainer
    val redisConnection by lazy {
        withRetries(
            feilmelding = "Klarte ikke koble til Redis.",
        ) {
            // Hijacker RedisClient her pga. vanskeligheter med å sette opp RedisContainer med SSL og autentisering
            mockStatic(RedisClient::class) {
                every { RedisClient.create(any<RedisURI>()) } returns RedisClient.create(redisContainer.redisURI)
                RedisConnection("host", 0, "username", "password")
            }
        }
    }

    val messages get() = imTestRapid.messages

    val imRepository by lazy { InntektsmeldingRepository(inntektsmeldingDatabase.db) }
    val selvbestemtImRepo by lazy { SelvbestemtImRepo(inntektsmeldingDatabase.db) }

    val bakgrunnsjobbRepository by lazy { PostgresBakgrunnsjobbRepository(bakgrunnsjobbDatabase.dataSource) }

    val altinnClient = mockk<Altinn3M2MClient>()
    val pdlKlient = mockk<PdlClient>()
    val spinnKlient = mockk<SpinnKlient>()

    val producer = mockk<Producer>(relaxed = true)
    val aaregClient = mockk<AaregClient>(relaxed = true)
    val agNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>(relaxed = true)
    val brregClient = mockk<BrregClient>(relaxed = true)
    val dokarkivClient = mockk<DokArkivClient>(relaxed = true)
    val inntektClient = mockk<InntektKlient>(relaxed = true)

    @BeforeEach
    fun beforeEachEndToEnd() {
        imTestRapid.reset()
        clearAllMocks()

        coEvery { pdlKlient.personBolk(any()) } returns listOf(bjarneBetjent, maxMekker)

        coEvery { brregClient.hentOrganisasjonNavn(any()) } answers {
            firstArg<Set<Orgnr>>().associateWith { "Bedrift A/S" }
        }
    }

    @BeforeAll
    fun beforeAllEndToEnd() {
        println("Starter rivers...")

        mockConnectToRapid(imTestRapid) {
            listOf(
                // Services
                createAktiveOrgnrService(it, redisConnection),
                createBerikInntektsmeldingService(it),
                createHentForespoerselServices(it, redisConnection),
                createHentSelvbestemtImService(it, redisConnection),
                createInnsendingServices(it, redisConnection, producer, taImotEksternInnsending = true),
                createInntektSelvbestemtService(it, redisConnection),
                createInntektService(it, redisConnection),
                createLagreSelvbestemtImService(it, redisConnection),
                createNotifikasjonServices(it),
                createTilgangServices(it, redisConnection),
                // Rivers
                createAaregRiver(aaregClient),
                createAltinn(altinnClient),
                createDbRivers(imRepository, selvbestemtImRepo),
                createDistribusjonRiver(producer),
                createForespoerselEventSwitch(producer),
                createHelsebroRivers(producer),
                createHentEksternImRiver(spinnKlient),
                createHentInntektRiver(inntektClient),
                createHentOrganisasjonNavnRiver(brregClient, false),
                createHentPersonerRiver(pdlKlient),
                createJournalfoerImRiver(dokarkivClient),
                createNotifikasjonRivers("notifikasjonLink", "P28D", agNotifikasjonKlient),
                // Feilbehandler
                createFeilLytter(it, bakgrunnsjobbRepository),
            ).flatten()
        }
    }

    @AfterAll
    fun afterAllEndToEnd() {
        redisConnection.close()
        inntektsmeldingDatabase.dataSource.close()
        bakgrunnsjobbDatabase.dataSource.close()
        println("Stopped.")
    }

    @JvmName("publishVarargPairKeyAndJsonElement")
    fun publish(vararg messageFields: Pair<Key, JsonElement>) {
        println("Publiserer melding med felt: ${messageFields.toMap()}")
        imTestRapid.publish(messageFields.toMap().mapKeys { (key, _) -> key.toString() })
    }

    @JvmName("publishVarargPairPriKeyAndJsonElement")
    fun publish(vararg messageFields: Pair<Pri.Key, JsonElement>) {
        println("Publiserer pri-melding med felt: ${messageFields.toMap()}")
        imTestRapid.publish(messageFields.toMap().mapKeys { (key, _) -> key.toString() })
    }

    fun mockForespoerselSvarFraHelsebro(
        forespoerselId: UUID,
        forespoerselSvar: ForespoerselFraBro?,
    ) {
        val messageSlot = slot<Map<Pri.Key, JsonElement>>()

        every {
            producer.send(
                key = any(),
                message = capture(messageSlot),
            )
        } answers {
            val message = messageSlot.captured
            val behov = message[Pri.Key.BEHOV]?.runCatching { fromJson(Pri.BehovType.serializer()) }?.getOrNull()

            if (behov == Pri.BehovType.TRENGER_FORESPØRSEL &&
                !message.containsKey(Pri.Key.LOESNING) &&
                message.containsKey(Pri.Key.FORESPOERSEL_ID) &&
                message.containsKey(Pri.Key.BOOMERANG)
            ) {
                publish(
                    Pri.Key.BEHOV to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(Pri.BehovType.serializer()),
                    Pri.Key.LOESNING to
                        ForespoerselSvar(
                            forespoerselId = forespoerselId,
                            resultat = forespoerselSvar,
                            feil =
                                if (forespoerselSvar == null) {
                                    ForespoerselSvar.Feil.FORESPOERSEL_IKKE_FUNNET
                                } else {
                                    null
                                },
                            boomerang = message[Pri.Key.BOOMERANG].shouldNotBeNull(),
                        ).toJson(ForespoerselSvar.serializer()),
                )
            }
        }
    }

    fun mockForespoerselSvarFraHelsebro(forespoerselListeSvar: List<ForespoerselFraBro>) {
        val messageSlot = slot<Map<Pri.Key, JsonElement>>()

        every {
            producer.send(
                key = any(),
                message = capture(messageSlot),
            )
        } answers {
            val message = messageSlot.captured
            val behov = message[Pri.Key.BEHOV]?.runCatching { fromJson(Pri.BehovType.serializer()) }?.getOrNull()

            if (behov == Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE &&
                !message.containsKey(Pri.Key.LOESNING) &&
                message.containsKey(Pri.Key.VEDTAKSPERIODE_ID_LISTE) &&
                message.containsKey(Pri.Key.BOOMERANG)
            ) {
                publish(
                    Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(Pri.BehovType.serializer()),
                    Pri.Key.LOESNING to
                        ForespoerselListeSvar(
                            resultat = forespoerselListeSvar,
                            boomerang = message[Pri.Key.BOOMERANG].shouldNotBeNull(),
                        ).toJson(ForespoerselListeSvar.serializer()),
                )
            }
        }
    }

    fun RedisConnection.get(
        prefix: RedisPrefix,
        kontekstId: UUID,
    ): String? = get("$prefix#$kontekstId")

    fun truncateDatabase() {
        transaction(inntektsmeldingDatabase.db) {
            exec("SELECT truncate_tables()")
        }
        transaction(bakgrunnsjobbDatabase.db) {
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

private fun <T> withRetries(
    antallForsoek: Int = 5,
    pauseMillis: Long = 1000,
    feilmelding: String,
    blokk: () -> T,
): T {
    repeat(antallForsoek) {
        runCatching { blokk() }
            .onSuccess { return it }
            .onFailure { runBlocking { delay(pauseMillis) } }
    }
    throw IllegalStateException(feilmelding)
}
