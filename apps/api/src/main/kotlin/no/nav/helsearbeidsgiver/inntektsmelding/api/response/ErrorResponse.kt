@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.response

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.hag.simba.utils.felles.Tekst
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.util.UUID

@Serializable
sealed class ErrorResponse {
    abstract fun statusCode(): HttpStatusCode

    abstract val kontekstId: UUID
    abstract val error: String
    abstract val errorId: String

    @Serializable
    @SerialName("Unknown")
    data class Unknown(
        override val kontekstId: UUID,
    ) : ErrorResponse() {
        override fun statusCode() = HttpStatusCode.InternalServerError

        @EncodeDefault
        override val error = "Ukjent feil."

        @EncodeDefault
        override val errorId = errorId(kontekstId)
    }

    @Serializable
    @SerialName("InvalidPathParameter")
    data class InvalidPathParameter(
        override val kontekstId: UUID,
        val parameterKey: String,
        val parameterValue: String?,
    ) : ErrorResponse() {
        override fun statusCode() = HttpStatusCode.BadRequest

        @EncodeDefault
        override val error = "Ugyldig stiparameter."

        @EncodeDefault
        override val errorId = errorId(kontekstId)
    }

    @Serializable
    @SerialName("ManglerTilgang")
    data class ManglerTilgang(
        override val kontekstId: UUID,
    ) : ErrorResponse() {
        override fun statusCode() = HttpStatusCode.Forbidden

        @EncodeDefault
        override val error = "Mangler rettigheter for organisasjon."

        @EncodeDefault
        override val errorId = errorId(kontekstId)
    }

    @Serializable
    @SerialName("JsonSerialization")
    data class JsonSerialization(
        override val kontekstId: UUID,
    ) : ErrorResponse() {
        override fun statusCode() = HttpStatusCode.BadRequest

        @EncodeDefault
        override val error = "Feil under serialisering eller deserialisering."

        @EncodeDefault
        override val errorId = errorId(kontekstId)
    }

    @Serializable
    @SerialName("Validering")
    data class Validering(
        override val kontekstId: UUID,
        val valideringsfeil: Set<String>,
    ) : ErrorResponse() {
        override fun statusCode() = HttpStatusCode.BadRequest

        @EncodeDefault
        override val error = "Feil under validering."

        @EncodeDefault
        override val errorId = errorId(kontekstId)
    }

    @Serializable
    @SerialName("NotFound")
    data class NotFound(
        override val kontekstId: UUID,
        override val error: String,
    ) : ErrorResponse() {
        override fun statusCode() = HttpStatusCode.NotFound

        @EncodeDefault
        override val errorId = errorId(kontekstId)
    }

    @Serializable
    @SerialName("RedisTimeout")
    data class RedisTimeout(
        override val kontekstId: UUID,
        val inntektsmeldingTypeId: UUID? = null,
    ) : ErrorResponse() {
        override fun statusCode() = HttpStatusCode.InternalServerError

        @EncodeDefault
        override val error = Tekst.REDIS_TIMEOUT_FEILMELDING

        @EncodeDefault
        override val errorId = errorId(kontekstId)
    }

    @Serializable
    @SerialName("Arbeidsforhold")
    data class Arbeidsforhold(
        override val kontekstId: UUID,
    ) : ErrorResponse() {
        override fun statusCode() = HttpStatusCode.BadRequest

        @EncodeDefault
        override val error = "Mangler arbeidsforhold i perioden."

        @EncodeDefault
        override val errorId = errorId(kontekstId)
    }
}

/** Bruker kort ID for at den skal være enklere å bruke for arbeidsgiver og NKS */
private fun errorId(kontekstId: UUID): String = kontekstId.toString().take(8)
