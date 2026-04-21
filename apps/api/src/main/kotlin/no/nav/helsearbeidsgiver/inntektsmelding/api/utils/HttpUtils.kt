package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.KSerializer
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import java.util.UUID

suspend fun <T : Any> RoutingContext.readPathParamOrError(
    kontekstId: UUID,
    param: Routes.Params.Param<T>,
    onSuccess: suspend RoutingContext.(T) -> Unit,
) {
    val paramValueString = call.parameters[param.key]

    val paramValue =
        paramValueString
            ?.takeUnless(String::isEmpty)
            ?.runCatching { param.transform(this) }
            ?.getOrNull()

    if (paramValue == null) {
        "Ugyldig parameter. key='${param.key}' value='$paramValueString'".also {
            logger.error(it)
            sikkerLogger.error(it)
        }
        respondError(ErrorResponse.InvalidPathParameter(kontekstId, param.key, paramValueString))
    } else {
        onSuccess(paramValue)
    }
}

suspend fun <T : Any> RoutingContext.readRequestOrError(
    kontekstId: UUID,
    requestSerializer: KSerializer<T>,
    onSuccess: suspend RoutingContext.(T) -> Unit,
) {
    val requestClassName = requestSerializer.descriptor.serialName.substringAfterLast(".")

    call
        .receiveText()
        .runCatching {
            parseJson()
                .also { json ->
                    "Mottok request som skal parses til '$requestClassName'.".let {
                        logger.info(it)
                        sikkerLogger.info("$it\n${json.toPretty()}")
                    }
                }.fromJson(requestSerializer)
        }.onFailure { error ->
            "Klarte ikke parse request til '$requestClassName'.".let {
                logger.error(it)
                sikkerLogger.error(it, error)
            }
            respondError(ErrorResponse.JsonSerialization(kontekstId))
        }.onSuccess { onSuccess(it) }
}

suspend fun <T : Any> RoutingContext.respondOk(
    response: T,
    serializer: KSerializer<T>,
) {
    respond(HttpStatusCode.OK, response, serializer)
}

suspend fun <T : Any> RoutingContext.respondCreated(
    response: T,
    serializer: KSerializer<T>,
) {
    respond(HttpStatusCode.Created, response, serializer)
}

suspend fun RoutingContext.respondError(error: ErrorResponse) {
    respond(error.statusCode(), error, ErrorResponse.serializer())
}

private suspend fun <T : Any> RoutingContext.respond(
    status: HttpStatusCode,
    response: T,
    serializer: KSerializer<T>,
) {
    call.respond(
        status = status,
        message = response.toJson(serializer),
    )
}
