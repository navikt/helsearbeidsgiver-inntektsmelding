@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.innsending.ekstern

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

@Serializable
data class AvvistInntektsmelding(
    val inntektsmeldingId: UUID,
    val forespoerselId: UUID,
    val vedtaksperiodeId: UUID,
    val orgnr: Orgnr,
    val feilkode: Feilkode,
)

enum class Feilkode {
    INNTEKT_AVVIKER_FRA_A_ORDNINGEN,
}
