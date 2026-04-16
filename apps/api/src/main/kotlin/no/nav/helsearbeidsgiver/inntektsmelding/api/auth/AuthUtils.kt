package no.nav.helsearbeidsgiver.inntektsmelding.api.auth

import io.ktor.http.auth.AuthScheme
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.authorization
import io.ktor.server.routing.RoutingContext
import no.nav.helsearbeidsgiver.inntektsmelding.api.Auth
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondError
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import no.nav.security.token.support.core.jwt.JwtToken
import java.util.UUID

suspend fun RoutingContext.validerTilgangForespoersel(
    tilgangskontroll: Tilgangskontroll,
    kontekstId: UUID,
    forespoerselId: UUID,
    onSuccess: suspend RoutingContext.() -> Unit,
) {
    val harTilgang = tilgangskontroll.harTilgangTilForespoersel(call.request, kontekstId, forespoerselId)
    validerTilgang(kontekstId, harTilgang) { onSuccess() }
}

suspend fun RoutingContext.validerTilgangOrgnr(
    tilgangskontroll: Tilgangskontroll,
    kontekstId: UUID,
    orgnr: Orgnr,
    onSuccess: suspend RoutingContext.() -> Unit,
) {
    val harTilgang = tilgangskontroll.harTilgangTilOrg(call.request, kontekstId, orgnr)
    validerTilgang(kontekstId, harTilgang) { onSuccess() }
}

private suspend fun RoutingContext.validerTilgang(
    kontekstId: UUID,
    harTilgang: Boolean?,
    onSuccess: suspend RoutingContext.() -> Unit,
) {
    when {
        harTilgang == null -> respondError(ErrorResponse.RedisTimeout(kontekstId))
        !harTilgang -> respondError(ErrorResponse.ManglerTilgang(kontekstId))
        else -> onSuccess()
    }
}

fun ApplicationRequest.lesFnrFraAuthToken(): Fnr {
    val authToken =
        authorization()?.removePrefix("${AuthScheme.Bearer} ")
            ?: throw IllegalAccessException("Mangler autorisasjonsheader.")

    val pid = JwtToken(authToken).jwtTokenClaims.getStringClaim(Auth.CLAIM_PID)

    val fnr = pid ?: JwtToken(authToken).subject

    if (!Fnr.erGyldig(fnr)) {
        throw IllegalAccessException("Fnr i autorisasjonsheader er ugyldig.")
    }

    return Fnr(fnr)
}
