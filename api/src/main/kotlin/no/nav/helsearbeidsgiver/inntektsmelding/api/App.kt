package no.nav.helsearbeidsgiver.inntektsmelding.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.lettuce.core.RedisClient
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.InnsendingProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.innsendingRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt.PreutfyltProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt.preutfyltRoute
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-api")

fun main() {
    val env = System.getenv()
    val redisUrl = env.get("REDIS_URL")
    val objectMapper = buildObjectMapper()
    val poller = RedisPoller(RedisClient.create("redis://$redisUrl:6379/0"), objectMapper)
    RapidApplication.create(env).apply {
        logger.info("Starter InnsendingProducer...")
        val innsendingProducer = InnsendingProducer(this)
        logger.info("Starter PreutfyltProducer...")
        val preutfyltProducer = PreutfyltProducer(this)
        embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
            install(ContentNegotiation) {
                jackson {
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    registerModule(JavaTimeModule())
                }
            }
            helsesjekkerRouting()
            routing {
                get("/") {
                    call.respondText("helsearbeidsgiver inntektsmelding")
                }
                route("/api/v1") {
                    arbeidsgiverRoute()
                    innsendingRoute(innsendingProducer, poller)
                    preutfyltRoute(preutfyltProducer, poller)
                }
            }
        }.start(wait = true)
    }.start()
}
