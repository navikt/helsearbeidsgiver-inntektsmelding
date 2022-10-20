@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.LocalDateSerializer
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isBefore
import org.valiktor.functions.isNotNull
import java.time.LocalDate

@Serializable
data class Egenmelding(
    val fom: LocalDate,
    val tom: LocalDate
) {
    fun validate() {
        org.valiktor.validate(this) {
            validate(Egenmelding::fom).isNotNull()
            validate(Egenmelding::tom).isNotNull()
            validate(Egenmelding::fom).isBefore(tom)
        }
    }
}
