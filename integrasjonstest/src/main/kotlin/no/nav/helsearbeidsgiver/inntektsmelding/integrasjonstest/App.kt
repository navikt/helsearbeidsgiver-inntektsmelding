package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.createAareg
import no.nav.helsearbeidsgiver.inntektsmelding.akkumulator.createAkkumulator
import no.nav.helsearbeidsgiver.inntektsmelding.altinn.createAltinn
import no.nav.helsearbeidsgiver.inntektsmelding.brreg.createBrreg
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.Database
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.DatabaseConfig
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.mapHikariConfig
import no.nav.helsearbeidsgiver.inntektsmelding.db.createDb
import no.nav.helsearbeidsgiver.inntektsmelding.distribusjon.createDistribusjon
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt.createForespoerselMottatt
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.PriProducer
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.createHelsebro
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.createInnsending
import no.nav.helsearbeidsgiver.inntektsmelding.inntekt.createInntekt
import no.nav.helsearbeidsgiver.inntektsmelding.joark.createJoark
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.createNotifikasjon
import no.nav.helsearbeidsgiver.inntektsmelding.pdl.createPdl
import no.nav.helsearbeidsgiver.inntektsmelding.preutfylt.createPreutfylt
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.utils.log.logger
import org.apache.kafka.clients.producer.KafkaProducer

val logger = "helsearbeidsgiver-im-integrasjon".logger()

fun main() {
    val env = mutableMapOf<String, String>()
    with(env) {
        put("KAFKA_RAPID_TOPIC", "helsearbeidsgiver.inntektsmelding")
        put("KAFKA_BOOTSTRAP_SERVERS", "PLAINTEXT://localhost:9092")
        put("KAFKA_CONSUMER_GROUP_ID", "HAG")
    }
    RapidApplication
        .create(env)
        .buildLocalApp()
        .start()
}

fun RapidsConnection.buildLocalApp(): RapidsConnection {
    val redisStore = RedisStore("redis://localhost:6379/0")
    val database = Database(mapHikariConfig(DatabaseConfig("127.0.0.1", "5432", "im_db", "postgres", "test")))
    val imoRepository = InntektsmeldingRepository(database.db)
    val forespoerselRepository = ForespoerselRepository(database.db)
    this.createAkkumulator(redisStore)
    this.createDb(database, imoRepository, forespoerselRepository)
    this.createForespoerselMottatt()
    return this
}

fun RapidsConnection.buildApp(
    redisStore: RedisStore,
    database: Database,
    imoRepository: InntektsmeldingRepository,
    forespoerselRepository: ForespoerselRepository,
    aaregClient: AaregClient,
    brregClient: BrregClient,
    inntektKlient: InntektKlient,
    dokarkivClient: DokArkivClient,
    pdlClient: PdlClient,
    arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    notifikasjonLink: String,
    priProducer: PriProducer,
    altinnClient: AltinnClient,
    distribusjonKafkaProducer: KafkaProducer<String, String>
): RapidsConnection {
    logger.info("Starting App")
    this.createAareg(aaregClient)
    this.createAkkumulator(redisStore)
    this.createBrreg(brregClient, true)
    this.createInnsending(redisStore)
    this.createDb(database, imoRepository, forespoerselRepository)
    this.createDistribusjon(distribusjonKafkaProducer)
    this.createForespoerselMottatt()
    this.createAltinn(altinnClient)
    this.createHelsebro(priProducer)
    this.createInntekt(inntektKlient)
    this.createJoark(dokarkivClient)
    this.createPdl(pdlClient)
    this.createPreutfylt()
    this.createNotifikasjon(redisStore, arbeidsgiverNotifikasjonKlient, notifikasjonLink)
    return this
}
