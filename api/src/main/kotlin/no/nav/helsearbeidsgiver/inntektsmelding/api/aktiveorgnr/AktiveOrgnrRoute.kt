package no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes

fun Route.aktiveOrgnrRoute() {
    route(Routes.AKTIVEORGNR) {
        post {
            val r = call.receive<AktiveOrgnrRequest>()
            call.respond(HttpStatusCode.Created, "{}")
        }
    }
}
