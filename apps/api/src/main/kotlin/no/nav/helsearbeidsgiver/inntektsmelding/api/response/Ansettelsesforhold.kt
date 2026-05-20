@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.response

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate
import no.nav.hag.simba.kontrakt.domene.ansettelsesforhold.Ansettelsesforhold as DomeneAnsettelsesforhold

@Serializable
data class Ansettelsesforhold(
    val startdato: LocalDate,
    val yrkesbeskrivelse: String?,
    val stillingsprosent: Double?,
) {
    companion object {
        fun fra(ansettelsesforhold: DomeneAnsettelsesforhold): Ansettelsesforhold =
            Ansettelsesforhold(
                startdato = ansettelsesforhold.startdato,
                yrkesbeskrivelse = ansettelsesforhold.yrkesbeskrivelse,
                stillingsprosent = ansettelsesforhold.stillingsprosent,
            )
    }
}
