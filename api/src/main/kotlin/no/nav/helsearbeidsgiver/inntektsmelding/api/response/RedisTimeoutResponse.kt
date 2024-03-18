@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.response

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.util.UUID

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class RedisTimeoutResponse(
    // TODO slett etter endring i frontend
    val uuid: UUID? = null,
    val inntektsmeldingTypeId: UUID? = null
) {
    @EncodeDefault
    val error = "Brukte for lang tid mot redis."
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class RedisPermanentErrorResponse(
    val inntektsmeldingTypeId: UUID
) {
    @EncodeDefault
    val error = "Permanent feil mot redis."
}
