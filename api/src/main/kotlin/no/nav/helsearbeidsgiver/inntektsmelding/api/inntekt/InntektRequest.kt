@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import org.valiktor.functions.isNotBlank
import org.valiktor.functions.isNotNull
import java.time.LocalDate

@Serializable
data class InntektRequest(
    val uuid: String,
    val fom: LocalDate
) {
    fun validate() {
        org.valiktor.validate(this) {
            validate(InntektRequest::uuid).isNotBlank()
            validate(InntektRequest::fom).isNotNull()
        }
    }

    // For å sikre at ikke orginal uuid-inntektrequest fortsatt ligger i Redis og gjenbrukes
    fun requestKey(): String {
        return uuid + "-" + fom.toString()
    }
}
