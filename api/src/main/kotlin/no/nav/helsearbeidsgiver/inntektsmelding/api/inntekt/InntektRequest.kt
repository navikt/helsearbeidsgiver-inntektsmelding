@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
import java.time.LocalDate
import java.util.UUID

@Serializable
data class InntektRequest(
    val forespoerselId: UUID,
    val skjaeringstidspunkt: LocalDate
) {

    // For å sikre at ikke orginal uuid-inntektrequest fortsatt ligger i Redis og gjenbrukes
    fun requestKey(): String {
        return forespoerselId.toString() + "-" + skjaeringstidspunkt.toString()
    }
}
