package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.utils.json.fromJson
import java.util.UUID

suspend fun <T : Any> RoutingContext.hentResultatFraRedisOrError(
    redisPoller: RedisPoller,
    kontekstId: UUID,
    inntektsmeldingTypeId: UUID? = null,
    logOnFailure: String,
    onFailureCustomError: ((JsonElement?) -> ErrorResponse?)? = null,
    successSerializer: KSerializer<T>,
    onSuccess: suspend RoutingContext.(T) -> Unit,
) {
    val result = redisPoller.hent(kontekstId)
    when {
        result != null -> {
            val success = result.success?.fromJson(successSerializer)
            if (success != null) {
                onSuccess(success)
            } else {
                val customError = onFailureCustomError?.invoke(result.failure)
                if (customError != null) {
                    respondError(customError)
                } else {
                    val feilmelding = result.failure?.fromJson(String.serializer())

                    logger.error(logOnFailure)
                    sikkerLogger.error("$logOnFailure Feilmelding: '$feilmelding'")

                    respondError(ErrorResponse.Unknown(kontekstId))
                }
            }
        }

        inntektsmeldingTypeId == null -> {
            respondError(ErrorResponse.RedisTimeout(kontekstId))
        }

        else -> {
            respondError(
                ErrorResponse.RedisTimeout(
                    kontekstId = kontekstId,
                    inntektsmeldingTypeId = inntektsmeldingTypeId,
                ),
            )
        }
    }
}
