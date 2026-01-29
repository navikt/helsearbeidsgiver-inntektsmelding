package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import no.nav.hag.simba.utils.felles.Tekst
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ErrorResponse
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson

suspend fun <T : Any> RoutingContext.receive(serializer: KSerializer<T>): T = call.receiveText().fromJson(serializer)

suspend fun <T : Any> RoutingContext.respondOk(
    message: T,
    serializer: KSerializer<T>,
) {
    respond(HttpStatusCode.OK, message, serializer)
}

suspend fun RoutingContext.respondBadRequest(message: ErrorResponse) {
    respond(HttpStatusCode.BadRequest, message, ErrorResponse.serializer())
}

@Deprecated("Feil bør returnere en ErrorResponse.")
suspend fun RoutingContext.respondBadRequest(message: String) {
    respond(HttpStatusCode.BadRequest, message, String.serializer())
}

@Deprecated("Feil bør returnere en ErrorResponse.")
suspend fun RoutingContext.respondForbidden(message: String) {
    respond(HttpStatusCode.Forbidden, message, String.serializer())
}

@Deprecated("Feil bør returnere en ErrorResponse.")
suspend fun RoutingContext.respondNotFound(message: String) {
    respond(HttpStatusCode.NotFound, message, String.serializer())
}

suspend fun RoutingContext.respondInternalServerError(message: ErrorResponse) {
    respond(HttpStatusCode.InternalServerError, message, ErrorResponse.serializer())
}

@Deprecated("Feil bør returnere en ErrorResponse.")
suspend fun RoutingContext.respondInternalServerError(message: String?) {
    respond(HttpStatusCode.InternalServerError, message ?: Tekst.TEKNISK_FEIL_FORBIGAAENDE, String.serializer())
}

suspend fun <T : Any> RoutingContext.respond(
    status: HttpStatusCode,
    message: T,
    serializer: KSerializer<T>,
) {
    call.respond(
        status = status,
        message = message.toJson(serializer),
    )
}
