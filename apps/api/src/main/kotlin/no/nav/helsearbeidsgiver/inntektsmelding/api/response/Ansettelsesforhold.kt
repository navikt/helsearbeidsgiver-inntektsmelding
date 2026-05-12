@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.response

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.hag.simba.utils.felles.domene.Ansettelsesforhold
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class Ansettelsesforhold(
    val startdato: LocalDate,
    val yrkesbeskrivelse: String?,
    val stillingsprosent: Double?,
) {
    companion object {
        fun fra(ansettelsesforhold: Ansettelsesforhold): no.nav.helsearbeidsgiver.inntektsmelding.api.response.Ansettelsesforhold =
            no.nav.helsearbeidsgiver.inntektsmelding.api.response.Ansettelsesforhold(
                startdato = ansettelsesforhold.startdato,
                yrkesbeskrivelse = ansettelsesforhold.yrkesbeskrivelse,
                stillingsprosent = ansettelsesforhold.stillingsprosent,
            )
    }
}
