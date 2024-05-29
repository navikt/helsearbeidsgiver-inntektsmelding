package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr.aktiveOrgnrRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.hentselvbestemtim.hentSelvbestemtImRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.innsendingRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt.inntektRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering.kvitteringRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.lagreselvbestemtim.lagreSelvbestemtImRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.trenger.trengerRoute
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

    private const val PREFIX_SELVBESTEMT_INNTEKTSMELDING = "/selvbestemt-inntektsmelding"

    const val TRENGER = "/trenger"
    const val INNTEKT = "/inntekt"
    const val INNSENDING = "/inntektsmelding"
    const val SELVBESTEMT_INNTEKTSMELDING_MED_ID = "$PREFIX_SELVBESTEMT_INNTEKTSMELDING/{selvbestemtId}"
    const val SELVBESTEMT_INNTEKTSMELDING_MED_VALGFRI_ID = "$PREFIX_SELVBESTEMT_INNTEKTSMELDING/{selvbestemtId?}"
    const val KVITTERING = "/kvittering"
    const val AKTIVEORGNR = "/aktiveorgnr"
}

fun main() {
    startServer()
}

fun startServer(env: Map<String, String> = System.getenv()) {
    val rapid = RapidApplication.create(env)

    embeddedServer(
        factory = Netty,
        port = 8080,
        module = { apiModule(rapid) }
    )
        .start(wait = true)

    rapid.start()
}

fun Application.apiModule(
    rapid: RapidsConnection,
    redisPoller: RedisPoller = RedisPoller()
) {
    val tilgangskontroll = Tilgangskontroll(
        TilgangProducer(rapid),
        LocalCache(60.minutes, 1000),
        redisPoller
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
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    HelsesjekkerRouting()

    routing {
        get("/") {
            call.respondText("helsearbeidsgiver inntektsmelding")
        }

        authenticate {
            route(Routes.PREFIX) {
                trengerRoute(rapid, tilgangskontroll, redisPoller)
                inntektRoute(rapid, tilgangskontroll, redisPoller)
                innsendingRoute(rapid, tilgangskontroll, redisPoller)
                kvitteringRoute(rapid, tilgangskontroll, redisPoller)
                lagreSelvbestemtImRoute(rapid, tilgangskontroll, redisPoller)
                hentSelvbestemtImRoute(rapid, tilgangskontroll, redisPoller)
                aktiveOrgnrRoute(rapid, redisPoller)
            }
        }
    }
}
