package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.helsearbeidsgiver.felles.metrics.Metrics

fun Application.helsesjekkerRouting() {
    routing {
        get("isalive") {
            call.respondText("I'm alive")
        }
        get("isready") {
            call.respondText("I'm ready")
        }
        get("metrics") {
            val names =
                call.request.queryParameters
                    .getAll("name[]")
                    ?.toSet()
                    .orEmpty()

            call.respondTextWriter(Metrics.Expose.contentType004) {
                Metrics.Expose.filteredMetricsWrite004(this, names)
            }
        }
    }
}
