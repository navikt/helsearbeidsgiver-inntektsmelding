package no.nav.helsearbeidsgiver.inntektsmelding.api.tilgangorgnr

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.validerTilgangOrgnr
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.readPathParam
import java.util.UUID

fun Route.tilgangOrgnrRoute(tilgangskontroll: Tilgangskontroll) {
    get(Routes.TILGANG_ORGNR) {
        val kontekstId = UUID.randomUUID()

        readPathParam(kontekstId, Routes.Params.orgnr) { orgnr ->
            validerTilgangOrgnr(tilgangskontroll, kontekstId, orgnr) {
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
