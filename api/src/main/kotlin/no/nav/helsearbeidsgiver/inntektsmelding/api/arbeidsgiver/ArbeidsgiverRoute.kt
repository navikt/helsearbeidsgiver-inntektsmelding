package no.nav.helsearbeidsgiver.inntektsmelding.api.arbeidsgiver

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.helsearbeidsgiver.inntektsmelding.api.mock.mockOrganisasjoner
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra

fun RouteExtra.ArbeidsgiverRoute() {
    route.route("/arbeidsgivere") {
        get {
            call.respond(mockOrganisasjoner())
        }
    }
}
