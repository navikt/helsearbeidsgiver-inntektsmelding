package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.KSerializer
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson

suspend fun <T : Any> PipelineContext<Unit, ApplicationCall>.receive(serializer: KSerializer<T>): T =
    call.receiveText().fromJson(serializer)

suspend fun <T : Any> PipelineContext<Unit, ApplicationCall>.respondOk(message: T, serializer: KSerializer<T>) {
    respond(HttpStatusCode.OK, message, serializer)
}

suspend fun <T : Any> PipelineContext<Unit, ApplicationCall>.respondBadRequest(message: T, serializer: KSerializer<T>) {
    respond(HttpStatusCode.BadRequest, message, serializer)
}

suspend fun <T : Any> PipelineContext<Unit, ApplicationCall>.respondForbidden(message: T, serializer: KSerializer<T>) {
    respond(HttpStatusCode.Forbidden, message, serializer)
}

suspend fun <T : Any> PipelineContext<Unit, ApplicationCall>.respondNotFound(message: T, serializer: KSerializer<T>) {
    respond(HttpStatusCode.NotFound, message, serializer)
}

suspend fun <T : Any> PipelineContext<Unit, ApplicationCall>.respondInternalServerError(message: T, serializer: KSerializer<T>) {
    respond(HttpStatusCode.InternalServerError, message, serializer)
}

suspend fun <T : Any> PipelineContext<Unit, ApplicationCall>.respond(status: HttpStatusCode, message: T, serializer: KSerializer<T>) {
    call.respond(
        status = status,
        message = message.toJson(serializer)
    )
}
