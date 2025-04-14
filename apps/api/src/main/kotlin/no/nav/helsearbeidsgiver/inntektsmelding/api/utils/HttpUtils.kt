package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.KSerializer
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson

suspend fun <T : Any> RoutingContext.receive(serializer: KSerializer<T>): T = call.receiveText().fromJson(serializer)

suspend fun <T : Any> RoutingContext.respondOk(
    message: T,
    serializer: KSerializer<T>,
) {
    respond(HttpStatusCode.OK, message, serializer)
}

suspend fun <T : Any> RoutingContext.respondBadRequest(
    message: T,
    serializer: KSerializer<T>,
) {
    respond(HttpStatusCode.BadRequest, message, serializer)
}

suspend fun <T : Any> RoutingContext.respondForbidden(
    message: T,
    serializer: KSerializer<T>,
) {
    respond(HttpStatusCode.Forbidden, message, serializer)
}

suspend fun <T : Any> RoutingContext.respondNotFound(
    message: T,
    serializer: KSerializer<T>,
) {
    respond(HttpStatusCode.NotFound, message, serializer)
}

suspend fun <T : Any> RoutingContext.respondInternalServerError(
    message: T,
    serializer: KSerializer<T>,
) {
    respond(HttpStatusCode.InternalServerError, message, serializer)
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
