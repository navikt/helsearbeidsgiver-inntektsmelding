package no.nav.helsearbeidsgiver.inntektsmelding.api.tilgangorgnr

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.validerTilgangOrgnrOrError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.readPathParamOrError
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.util.UUID

fun Route.tilgangOrgnrRoute(tilgangskontroll: Tilgangskontroll) {
    get(Routes.TILGANG_ORGNR) {
        val kontekstId = UUID.randomUUID()

        MdcUtils.withLogFields(
            Log.apiRoute(Routes.TILGANG_ORGNR),
            Log.kontekstId(kontekstId),
        ) {
            readPathParamOrError(kontekstId, Routes.Params.orgnr) { orgnr ->
                validerTilgangOrgnrOrError(tilgangskontroll, kontekstId, orgnr) {
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}
