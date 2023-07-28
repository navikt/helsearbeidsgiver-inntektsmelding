package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.akkumulator.createAkkumulator
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.Database
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.DatabaseConfig
import no.nav.helsearbeidsgiver.inntektsmelding.db.config.mapHikariConfig
import no.nav.helsearbeidsgiver.inntektsmelding.db.createDb

fun main() {
    val env = mapOf(
        "KAFKA_RAPID_TOPIC" to "helsearbeidsgiver.inntektsmelding",
        "KAFKA_BOOTSTRAP_SERVERS" to "PLAINTEXT://localhost:9092",
        "KAFKA_CONSUMER_GROUP_ID" to "HAG"
    )

    RapidApplication
        .create(env)
        .buildLocalApp()
        .start()
}

fun RapidsConnection.buildLocalApp(): RapidsConnection =
    also {
        val redisStore = RedisStore("redis://localhost:6379/0")
        val database = Database(
            mapHikariConfig(
                DatabaseConfig(
                    host = "127.0.0.1",
                    port = "5432",
                    name = "im_db",
                    username = "postgres",
                    password = "test"
                )
            )
        )
        val imRepository = InntektsmeldingRepository(database.db)
        val forespoerselRepository = ForespoerselRepository(database.db)

        createAkkumulator(redisStore)
        createDb(database, imRepository, forespoerselRepository)
//        createForespoerselMottatt()
    }
