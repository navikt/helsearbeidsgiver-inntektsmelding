package no.nav.helsearbeidsgiver.inntektsmelding.api.arbeidsgiver

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.helsearbeidsgiver.inntektsmelding.api.mock.mockOrganisasjoner

fun Route.ArbeidsgiverRoute() {
    route("/arbeidsgivere") {
        get {
            call.respond(mockOrganisasjoner())
        }
    }
}
