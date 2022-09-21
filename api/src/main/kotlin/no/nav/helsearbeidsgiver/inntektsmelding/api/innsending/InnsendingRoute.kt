package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.lettuce.core.RedisClient
import java.util.concurrent.TimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger

fun Route.innsending(producer: InnsendingProducer, redisUrl: String) {
    route("/inntektsmelding") {
        post {
            val request = call.receive<InntektsmeldingRequest>()
            logger.info("Mottok inntektsmelding $request")
            request.validate()
            logger.info("Publiser til Rapid")
            val uuid = producer.publish(request)
            val poller = RedisPoller(RedisClient.create("redis://$redisUrl:6379/0"))
            try {
                val value = poller.getValue(uuid, 5, 500)
                call.respond(HttpStatusCode.Created, value)
            } catch (ex: TimeoutException) {
                call.respond(HttpStatusCode.InternalServerError, "Klarte ikke hente data")
            } finally {
                poller.shutdown()
            }
        }
    }
}
