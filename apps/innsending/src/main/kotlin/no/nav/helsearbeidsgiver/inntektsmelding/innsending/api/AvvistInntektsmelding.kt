@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.innsending.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.util.UUID

@Serializable
data class AvvistInntektsmelding(
    val inntektsmeldingId: UUID,
    val feilkode: Feilkode,
)

enum class Feilkode {
    INNTEKT_AVVIKER_FRA_A_ORDNINGEN,
}
