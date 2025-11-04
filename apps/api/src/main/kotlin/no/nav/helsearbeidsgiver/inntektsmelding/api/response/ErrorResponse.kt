@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.response

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.hag.simba.utils.felles.Tekst
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.util.UUID

@Serializable
@OptIn(ExperimentalSerializationApi::class)
sealed class ErrorResponse {
    abstract val kontekstId: UUID
    abstract val error: String
    abstract val errorId: String

    companion object {
        /** Bruker kort ID for at den skal være enklere å bruke for arbeidsgiver og NKS */
        fun errorId(kontekstId: UUID): String = kontekstId.toString().take(8)
    }

    @Serializable
    @SerialName("Unknown")
    data class Unknown(
        override val kontekstId: UUID,
    ) : ErrorResponse() {
        @EncodeDefault
        override val error = "Ukjent feil."

        @EncodeDefault
        override val errorId = errorId(kontekstId)
    }

    @Serializable
    @SerialName("JsonSerialization")
    data class JsonSerialization(
        override val kontekstId: UUID,
        // TODO slett etter endring i frontend
        val forespoerselId: String? = null,
        val inntektsmeldingTypeId: UUID? = null,
    ) : ErrorResponse() {
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
        @EncodeDefault
        override val error = "Feil under validering."

        @EncodeDefault
        override val errorId = errorId(kontekstId)
    }

    @Serializable
    @SerialName("RedisTimeout")
    data class RedisTimeout(
        override val kontekstId: UUID,
        // TODO slett etter endring i frontend
        val uuid: UUID? = null,
        val inntektsmeldingTypeId: UUID? = null,
    ) : ErrorResponse() {
        @EncodeDefault
        override val error = Tekst.REDIS_TIMEOUT_FEILMELDING

        @EncodeDefault
        override val errorId = errorId(kontekstId)
    }

    @Serializable
    @SerialName("Arbeidsforhold")
    data class Arbeidsforhold(
        override val kontekstId: UUID,
        val inntektsmeldingTypeId: UUID? = null,
    ) : ErrorResponse() {
        @EncodeDefault
        override val error = "Mangler arbeidsforhold i perioden"

        @EncodeDefault
        override val errorId = errorId(kontekstId)
    }
}
