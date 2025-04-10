package no.nav.helsearbeidsgiver.inntektsmelding.api.tilgangorgnr

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

fun Route.tilgangOrgnrRoute(tilgangskontroll: Tilgangskontroll) {
    get(Routes.TILGANG_ORGNR) {
        val orgnr = call.parameters["orgnr"]
        try {
            if (orgnr.isNullOrEmpty() || !Orgnr.erGyldig(orgnr)) {
                call.respond(HttpStatusCode.BadRequest, "Ugyldig orgnr")
            } else {
                tilgangskontroll.validerTilgangTilOrg(call.request, orgnr)
                call.respond(HttpStatusCode.OK)
            }
        } catch (_: ManglerAltinnRettigheterException) {
            call.respond(HttpStatusCode.Forbidden, "Du har ikke rettigheter for organisasjon.")
        } catch (_: RedisPollerTimeoutException) {
            "Fikk timeout for tilgangsjekk orgnr.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
            call.respond(HttpStatusCode.InternalServerError, "Teknisk feil")
        } catch (e: Exception) {
            "Ukjent feil".also {
                logger.error(it)
                sikkerLogger.error(it, e)
            }
            call.respond(HttpStatusCode.InternalServerError, "Teknisk feil")
        }
    }
}
