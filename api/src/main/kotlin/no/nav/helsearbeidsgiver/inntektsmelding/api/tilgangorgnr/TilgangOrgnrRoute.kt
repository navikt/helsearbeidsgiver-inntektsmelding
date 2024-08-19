package no.nav.helsearbeidsgiver.inntektsmelding.api.tilgangorgnr

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondForbidden
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

fun Route.tilgangOrgnrRoute(tilgangskontroll: Tilgangskontroll) {
    get(Routes.TILGANG_ORGNR) {
        val orgnr = call.parameters["orgnr"]
        try {
            if (orgnr.isNullOrEmpty() || !Orgnr.erGyldig(orgnr)) {
                call.respond(HttpStatusCode.BadRequest, "Ugyldig orgnr")
            } else {
                tilgangskontroll.validerTilgangTilOrg(call.request, orgnr)
            }
        } catch (e: ManglerAltinnRettigheterException) {
            respondForbidden("Du har ikke rettigheter for organisasjon.", String.serializer())
        } catch (_: RedisPollerTimeoutException) {
            logger.error("Fikk timeout for tilgangsjekk orgnr.")
            sikkerLogger.error("Fikk timeout for tilgangsjekk orgnr.")
            respondInternalServerError(RedisTimeoutResponse(), RedisTimeoutResponse.serializer())
        }
    }
}
