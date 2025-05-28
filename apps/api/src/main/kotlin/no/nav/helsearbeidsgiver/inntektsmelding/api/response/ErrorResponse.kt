@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.response

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.util.UUID

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class UkjentErrorResponse(
    val inntektsmeldingTypeId: UUID? = null,
) {
    @EncodeDefault
    val error = "Ukjent feil."
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class JsonErrorResponse(
    // TODO slett etter endring i frontend
    val forespoerselId: String? = null,
    val inntektsmeldingTypeId: UUID? = null,
) {
    @EncodeDefault
    val error = "Feil under serialisering."
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ValideringErrorResponse(
    val valideringsfeil: Set<String>,
) {
    @EncodeDefault
    val error = "Feil under validering."
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class RedisTimeoutResponse(
    // TODO slett etter endring i frontend
    val uuid: UUID? = null,
    val inntektsmeldingTypeId: UUID? = null,
) {
    @EncodeDefault
    val error = Tekst.REDIS_TIMEOUT_FEILMELDING
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class RedisPermanentErrorResponse(
    val inntektsmeldingTypeId: UUID? = null,
) {
    @EncodeDefault
    val error = "Permanent feil mot redis."
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ArbeidsforholdErrorResponse(
    val inntektsmeldingTypeId: UUID? = null,
) {
    @EncodeDefault
    val error = "Mangler arbeidsforhold i perioden"
}
