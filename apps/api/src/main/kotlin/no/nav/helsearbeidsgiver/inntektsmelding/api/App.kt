package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.addShutdownHook
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.builtins.serializer
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr.aktiveOrgnrRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoersel.hentForespoersel
import no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoerselIdListe.hentForespoerselIdListe
import no.nav.helsearbeidsgiver.inntektsmelding.api.hentselvbestemtim.hentSelvbestemtImRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.innsending
import no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt.inntektRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.inntektselvbestemt.inntektSelvbestemtRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering.kvittering
import no.nav.helsearbeidsgiver.inntektsmelding.api.lagreselvbestemtim.lagreSelvbestemtImRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgangorgnr.tilgangOrgnrRoute
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import kotlin.time.Duration.Companion.minutes

val logger = "helsearbeidsgiver-im-api".logger()
val sikkerLogger = sikkerLogger()

object Routes {
    const val PREFIX = "/api/v1"

    const val HENT_FORESPOERSEL = "/hent-forespoersel/{forespoerselId}"
    const val HENT_FORESPOERSEL_ID_LISTE = "/hent-forespoersel-id-liste"
    const val INNTEKT = "/inntekt"
    const val INNTEKT_SELVBESTEMT = "/inntekt-selvbestemt"
    const val INNSENDING = "/inntektsmelding"
    const val SELVBESTEMT_INNTEKTSMELDING = "/selvbestemt-inntektsmelding"
    const val SELVBESTEMT_INNTEKTSMELDING_MED_ID = "$SELVBESTEMT_INNTEKTSMELDING/{selvbestemtId}"
    const val KVITTERING = "/kvittering/{forespoerselId}"
    const val AKTIVEORGNR = "/aktiveorgnr"
    const val TILGANG_ORGNR = "/tilgangorgnr/{orgnr}"
}

fun main() {
    val producer = Producer(Env.kafkaTopic)
    val redisConnection =
        RedisConnection(
            host = Env.Redis.host,
            port = Env.Redis.port,
            username = Env.Redis.username,
            password = Env.Redis.password,
        )

    embeddedServer(
        factory = Netty,
        port = 8080,
        module = { apiModule(producer, redisConnection) },
    ).apply {
        addShutdownHook {
            producer.close()
            redisConnection.close()
        }
    }.start(wait = true)
}

fun Application.apiModule(
    producer: Producer,
    redisConnection: RedisConnection,
) {
    val tilgangskontroll =
        Tilgangskontroll(
            producer,
            LocalCache(LocalCache.Config(60.minutes, 5000)),
            redisConnection,
        )

    customAuthentication()

    install(ContentNegotiation) {
        json(jsonConfig)
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            "Ukjent feil.".also {
                logger.error(it)
                sikkerLogger.error(it, cause)
            }

            call.respondText(
                text = "Error 500: $cause".toJsonStr(String.serializer()),
                status = HttpStatusCode.InternalServerError,
            )
        }
    }

    helsesjekkerRouting()

    routing {
        get("/") {
            call.respondText("helsearbeidsgiver inntektsmelding")
        }

        authenticate {
            route(Routes.PREFIX) {
                hentForespoersel(producer, tilgangskontroll, redisConnection)
                hentForespoerselIdListe(producer, tilgangskontroll, redisConnection)
                inntektRoute(producer, tilgangskontroll, redisConnection)
                inntektSelvbestemtRoute(producer, tilgangskontroll, redisConnection)
                innsending(producer, tilgangskontroll, redisConnection)
                kvittering(producer, tilgangskontroll, redisConnection)
                lagreSelvbestemtImRoute(producer, tilgangskontroll, redisConnection)
                hentSelvbestemtImRoute(producer, tilgangskontroll, redisConnection)
                aktiveOrgnrRoute(producer, redisConnection)
                tilgangOrgnrRoute(tilgangskontroll)
            }
        }
    }
}
