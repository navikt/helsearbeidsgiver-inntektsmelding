package no.nav.helsearbeidsgiver.inntektsmelding.api.tilgangorgnr

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondBadRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondForbidden
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

fun Route.tilgangOrgnrRoute(tilgangskontroll: Tilgangskontroll) {
    get(Routes.TILGANG_ORGNR) {
        val orgnr = call.parameters["orgnr"]
        try {
            if (orgnr.isNullOrEmpty() || !Orgnr.erGyldig(orgnr)) {
                respondBadRequest("Ugyldig orgnr")
            } else {
                tilgangskontroll.validerTilgangTilOrg(call.request, Orgnr(orgnr))
                call.respond(HttpStatusCode.OK)
            }
        } catch (_: ManglerAltinnRettigheterException) {
            respondForbidden("Du har ikke rettigheter for organisasjon.")
        }
    }
}
