@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.util.UUID

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class InnsendingResponse(
    val uuid: UUID,
) {
    @EncodeDefault
    val forespoerselId: UUID = uuid
}
