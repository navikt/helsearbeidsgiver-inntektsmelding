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
data class JsonErrorResponse(
    // TODO slett etter endring i frontend
    val forespoerselId: String? = null,
    val inntektsmeldingId: UUID? = null
) {
    @EncodeDefault
    val error = "Feil under serialisering."
}
